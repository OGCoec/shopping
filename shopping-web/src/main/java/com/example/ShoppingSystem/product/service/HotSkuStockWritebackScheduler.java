package com.example.ShoppingSystem.product.service;

import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.mapper.product.ProductHotSkuMapper;
import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class HotSkuStockWritebackScheduler {

    private static final Logger log = LoggerFactory.getLogger(HotSkuStockWritebackScheduler.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductHotSkuMapper productHotSkuMapper;
    private final ObjectMapper objectMapper;
    private final RoutedTransactionExecutor routedTransactionExecutor;
    private final DefaultRedisScript<List> cleanupScript;
    private final DefaultRedisScript<Long> unlockScript;
    private final int batchSize;
    private final int maxBatchesPerRun;
    private final Duration lockTtl;

    public HotSkuStockWritebackScheduler(StringRedisTemplate stringRedisTemplate,
                                         ProductHotSkuMapper productHotSkuMapper,
                                         ObjectMapper objectMapper,
                                         RoutedTransactionExecutor routedTransactionExecutor,
                                         @Value("${shopping.product.hot-sku.stock-writeback-batch-size:200}") int batchSize,
                                         @Value("${shopping.product.hot-sku.stock-writeback-max-batches-per-run:10}") int maxBatchesPerRun,
                                         @Value("${shopping.product.hot-sku.stock-writeback-lock-ttl-ms:30000}") long lockTtlMs) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.productHotSkuMapper = productHotSkuMapper;
        this.objectMapper = objectMapper;
        this.routedTransactionExecutor = routedTransactionExecutor;
        this.cleanupScript = listRedisScript("lua/hot_sku_stock_dirty_cleanup.lua");
        this.unlockScript = longRedisScript("lua/order_persist_unlock.lua");
        this.batchSize = batchSize <= 0 ? 200 : batchSize;
        this.maxBatchesPerRun = Math.max(1, maxBatchesPerRun);
        this.lockTtl = Duration.ofMillis(Math.max(1000L, lockTtlMs));
    }

    @Scheduled(fixedDelayString = "${shopping.product.hot-sku.stock-writeback-delay-ms:5000}")
    public void writebackDirtyStock() {
        String lockValue = UUID.randomUUID().toString();
        if (!acquireLock(lockValue)) {
            return;
        }
        try {
            writebackDirtyStockWithLock();
        } finally {
            releaseLock(lockValue);
        }
    }

    private void writebackDirtyStockWithLock() {
        int batchCount = 0;
        int totalRead = 0;
        int totalWritten = 0;
        int totalCleaned = 0;
        boolean failed = false;
        while (batchCount < maxBatchesPerRun) {
            List<String> skuIds = readDirtySkuIds();
            if (skuIds.isEmpty()) {
                break;
            }
            totalRead += skuIds.size();
            WritebackBatch batch = buildBatch(skuIds);
            removeInvalidDirtySkuIds(batch.invalidSkuIds());
            if (batch.rows().isEmpty()) {
                if (skuIds.size() < batchSize) {
                    break;
                }
                batchCount++;
                continue;
            }
            try {
                WritebackResult result = writebackRows(batch.rows());
                validateWritebackResult(result);
                totalWritten += result.hotUpdatedCount();
                totalCleaned += cleanupDirty(batch.cleanupItems());
                batchCount++;
            } catch (Exception e) {
                failed = true;
                log.warn("[Product Hot SKU] stock writeback batch failed, batch={}, skuCount={}",
                        batchCount + 1, batch.rows().size(), e);
                break;
            }
            if (skuIds.size() < batchSize) {
                break;
            }
        }
        if (batchCount > 0 || failed) {
            log.info("[Product Hot SKU] stock writeback finished, batches={}, read={}, written={}, cleaned={}, batchSize={}, maxBatches={}, failed={}",
                    batchCount, totalRead, totalWritten, totalCleaned, batchSize, maxBatchesPerRun, failed);
        }
    }

    private List<String> readDirtySkuIds() {
        Set<String> members = stringRedisTemplate.opsForSet()
                .distinctRandomMembers(OrderRedisKeys.HOT_SKU_STOCK_DIRTY_KEY, batchSize);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private WritebackBatch buildBatch(List<String> skuIds) {
        List<ValidatedSkuId> validSkuIds = new ArrayList<>(skuIds.size());
        List<String> invalidSkuIds = new ArrayList<>();
        for (String skuId : skuIds) {
            try {
                validSkuIds.add(new ValidatedSkuId(
                        skuId,
                        ProductSkuIdCodec.toHex(ProductSkuIdCodec.fromBase62(skuId))
                ));
            } catch (IllegalArgumentException e) {
                invalidSkuIds.add(skuId);
                log.warn("[Product Hot SKU] invalid dirty SKU id skipped, skuId={}", skuId);
            }
        }
        if (validSkuIds.isEmpty()) {
            return new WritebackBatch(List.of(), List.of(), invalidSkuIds);
        }
        List<String> stockKeys = validSkuIds.stream()
                .map(item -> OrderRedisKeys.hotSkuStockKey(item.skuId()))
                .toList();
        List<String> stockValues = stringRedisTemplate.opsForValue().multiGet(stockKeys);
        List<Map<String, Object>> rows = new ArrayList<>(validSkuIds.size());
        List<CleanupItem> cleanupItems = new ArrayList<>(validSkuIds.size());
        for (int index = 0; index < validSkuIds.size(); index += 1) {
            ValidatedSkuId skuId = validSkuIds.get(index);
            String stockValue = stockValues == null || index >= stockValues.size() ? null : stockValues.get(index);
            Integer remaining = parseRemaining(skuId.skuId(), stockValue);
            if (remaining == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sku_id_hex", skuId.skuIdHex());
            row.put("sku_id_text", skuId.skuId());
            row.put("remaining_quantity", remaining);
            rows.add(row);
            cleanupItems.add(new CleanupItem(skuId.skuId(), remaining));
        }
        return new WritebackBatch(rows, cleanupItems, invalidSkuIds);
    }

    private Integer parseRemaining(String skuId, String stockValue) {
        if (stockValue == null || stockValue.isBlank()) {
            log.warn("[Product Hot SKU] dirty stock key missing, skuId={}", skuId);
            return null;
        }
        try {
            int remaining = Integer.parseInt(stockValue.trim());
            if (remaining < 0) {
                log.warn("[Product Hot SKU] dirty stock value negative, skuId={}, value={}", skuId, stockValue);
                return null;
            }
            return remaining;
        } catch (NumberFormatException e) {
            log.warn("[Product Hot SKU] dirty stock value invalid, skuId={}, value={}", skuId, stockValue);
            return null;
        }
    }

    private WritebackResult writebackRows(List<Map<String, Object>> rows) throws JsonProcessingException {
        String itemsJson = objectMapper.writeValueAsString(rows);
        Map<String, Object> result = routedTransactionExecutor.execute(DataSourceRoute.PRODUCT,
                () -> productHotSkuMapper.batchWritebackRuntimeStock(itemsJson));
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("Hot SKU stock writeback result is empty.");
        }
        return new WritebackResult(
                intValue(result.get("requestedCount")),
                intValue(result.get("matchedCount")),
                intValue(result.get("invalidCount")),
                intValue(result.get("hotUpdatedCount")),
                intValue(result.get("skuUpdatedCount"))
        );
    }

    private void validateWritebackResult(WritebackResult result) {
        if (result.invalidCount() > 0) {
            throw new IllegalStateException("Hot SKU stock writeback contains invalid stock rows.");
        }
        if (result.hotUpdatedCount() != result.matchedCount()
                || result.skuUpdatedCount() != result.matchedCount()) {
            throw new IllegalStateException("Hot SKU stock writeback updated count mismatch.");
        }
        if (result.matchedCount() < result.requestedCount()) {
            log.info("[Product Hot SKU] dirty stock contains removed hot SKU rows, requested={}, matched={}",
                    result.requestedCount(), result.matchedCount());
        }
    }

    private int cleanupDirty(List<CleanupItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        List<String> keys = new ArrayList<>(items.size() + 1);
        keys.add(OrderRedisKeys.HOT_SKU_STOCK_DIRTY_KEY);
        keys.addAll(items.stream()
                .map(item -> OrderRedisKeys.hotSkuStockKey(item.skuId()))
                .toList());
        List<Object> args = new ArrayList<>(items.size() * 2 + 1);
        args.add(String.valueOf(items.size()));
        for (CleanupItem item : items) {
            args.add(item.skuId());
            args.add(String.valueOf(item.remainingQuantity()));
        }
        List<?> result = stringRedisTemplate.execute(cleanupScript, keys, args.toArray(new Object[0]));
        Integer cleaned = intAt(result, 0);
        return cleaned == null ? 0 : cleaned;
    }

    private void removeInvalidDirtySkuIds(List<String> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForSet().remove(
                OrderRedisKeys.HOT_SKU_STOCK_DIRTY_KEY,
                skuIds.toArray(new Object[0])
        );
    }

    private boolean acquireLock(String lockValue) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                OrderRedisKeys.HOT_SKU_STOCK_WRITEBACK_LOCK_KEY,
                lockValue,
                lockTtl
        );
        return Boolean.TRUE.equals(acquired);
    }

    private void releaseLock(String lockValue) {
        if (lockValue == null || lockValue.isBlank()) {
            return;
        }
        stringRedisTemplate.execute(
                unlockScript,
                List.of(OrderRedisKeys.HOT_SKU_STOCK_WRITEBACK_LOCK_KEY),
                lockValue
        );
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(text);
    }

    private Integer intAt(List<?> result, int index) {
        if (result == null || result.size() <= index || result.get(index) == null) {
            return null;
        }
        Object value = result.get(index);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private DefaultRedisScript<List> listRedisScript(String location) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(List.class);
        return script;
    }

    private DefaultRedisScript<Long> longRedisScript(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(Long.class);
        return script;
    }

    private record WritebackBatch(List<Map<String, Object>> rows,
                                  List<CleanupItem> cleanupItems,
                                  List<String> invalidSkuIds) {
    }

    private record CleanupItem(String skuId, int remainingQuantity) {
    }

    private record ValidatedSkuId(String skuId, String skuIdHex) {
    }

    private record WritebackResult(int requestedCount,
                                   int matchedCount,
                                   int invalidCount,
                                   int hotUpdatedCount,
                                   int skuUpdatedCount) {
    }
}
