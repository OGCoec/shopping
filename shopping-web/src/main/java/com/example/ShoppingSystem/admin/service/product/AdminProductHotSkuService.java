package com.example.ShoppingSystem.admin.service.product;

import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuBatchEnableRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuBatchResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuEnableItem;
import com.example.ShoppingSystem.admin.dto.AdminProductHotSkuResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuBatchIdsRequest;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.mapper.product.ProductHotSkuMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class AdminProductHotSkuService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_SOLD_OUT = "SOLD_OUT";
    private static final Set<String> SUPPORTED_STATUS = Set.of(STATUS_ENABLED, STATUS_DISABLED, STATUS_SOLD_OUT);
    private static final String HOT_SKU_META_KEY_PREFIX = "shopping:product:hot-sku:meta:";
    private static final String HOT_SKU_STOCK_KEY_PREFIX = "shopping:product:hot-sku:stock:";

    private final ProductHotSkuMapper productHotSkuMapper;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public AdminProductHotSkuService(ProductHotSkuMapper productHotSkuMapper,
                                     HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                     StringRedisTemplate stringRedisTemplate,
                                     ObjectMapper objectMapper) {
        this.productHotSkuMapper = productHotSkuMapper;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<AdminProductHotSkuResponse> listHotSkus(Long rawSpuId) {
        Long spuId = normalizeRequiredId(rawSpuId, "商品 ID");
        List<Map<String, Object>> rows = productHotSkuMapper.listHotSkusBySpuId(spuId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminProductHotSkuBatchResponse batchEnable(Long rawSpuId, AdminProductHotSkuBatchEnableRequest request) {
        Long spuId = normalizeRequiredId(rawSpuId, "商品 ID");
        List<NormalizedHotSkuEnableItem> items = normalizeEnableItems(request == null ? null : request.items());
        Map<String, Object> result = productHotSkuMapper.upsertHotSkus(spuId, toItemsJson(items));
        validateEnableResult(result, items.size());
        List<RedisHotSkuItem> redisItems = parseRedisItems(value(result, "redisItemsJson"));
        runAfterCommit(() -> writeHotSkusToRedis(spuId, redisItems));
        return new AdminProductHotSkuBatchResponse(
                toInt(value(result, "requestedCount"), items.size()),
                toInt(value(result, "matchedCount"), 0),
                toInt(value(result, "affectedCount"), 0));
    }

    @Transactional
    public AdminProductHotSkuBatchResponse batchDelete(Long rawSpuId, AdminProductSkuBatchIdsRequest request) {
        Long spuId = normalizeRequiredId(rawSpuId, "商品 ID");
        List<String> skuIds = normalizeBatchSkuIds(request == null ? null : request.ids());
        Map<String, Object> result = productHotSkuMapper.deleteHotSkusBySkuIds(spuId, skuIdBytes(skuIds));
        if (!toBoolean(value(result, "spuExists"))) {
            throw productNotFoundException();
        }
        runAfterCommit(() -> deleteHotSkusFromRedis(skuIds));
        return new AdminProductHotSkuBatchResponse(
                toInt(value(result, "requestedCount"), skuIds.size()),
                toInt(value(result, "matchedCount"), 0),
                toInt(value(result, "affectedCount"), 0));
    }

    private List<NormalizedHotSkuEnableItem> normalizeEnableItems(List<AdminProductHotSkuEnableItem> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_HOT_SKU_BATCH_EMPTY",
                    "请选择需要设置为热点的 SKU。",
                    HttpStatus.BAD_REQUEST);
        }
        List<NormalizedHotSkuEnableItem> items = new ArrayList<>();
        Set<String> seenSkuIds = new LinkedHashSet<>();
        for (AdminProductHotSkuEnableItem rawItem : rawItems) {
            String skuId = normalizeSkuId(rawItem == null ? null : rawItem.skuId());
            if (!seenSkuIds.add(skuId)) {
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_HOT_SKU_DUPLICATE",
                        "热点 SKU 不能重复。",
                        HttpStatus.BAD_REQUEST);
            }
            int stockQuantity = normalizeStockQuantity(rawItem == null ? null : rawItem.stockQuantity());
            String status = normalizeHotStatus(rawItem == null ? null : rawItem.status());
            OffsetDateTime startAt = parseOptionalDateTime(rawItem == null ? null : rawItem.startAt(), "开始时间");
            OffsetDateTime endAt = parseOptionalDateTime(rawItem == null ? null : rawItem.endAt(), "结束时间");
            if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_HOT_SKU_TIME_INVALID",
                        "热点结束时间必须晚于开始时间。",
                        HttpStatus.BAD_REQUEST);
            }
            byte[] skuIdBytes = skuIdBytes(skuId);
            byte[] hotIdBytes = hybridSemaphoreIdWorker.nextId();
            items.add(new NormalizedHotSkuEnableItem(
                    ProductSkuIdCodec.toHex(hotIdBytes),
                    skuId,
                    ProductSkuIdCodec.toHex(skuIdBytes),
                    stockQuantity,
                    status,
                    toEpochMillis(startAt),
                    toEpochMillis(endAt)));
        }
        return List.copyOf(items);
    }

    private String toItemsJson(List<NormalizedHotSkuEnableItem> items) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (NormalizedHotSkuEnableItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sku_id", item.skuId());
            row.put("sku_id_bytes_hex", item.skuIdBytesHex());
            row.put("hot_id_bytes_hex", item.hotIdBytesHex());
            row.put("stock_quantity", item.stockQuantity());
            row.put("status", item.status());
            row.put("start_at_epoch_ms", item.startAtEpochMs());
            row.put("end_at_epoch_ms", item.endAtEpochMs());
            rows.add(row);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_HOT_SKU_JSON_FAILED",
                    "热点 SKU 参数序列化失败。",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateEnableResult(Map<String, Object> result, int requestedCount) {
        if (!toBoolean(value(result, "spuExists"))) {
            throw productNotFoundException();
        }
        int matchedCount = toInt(value(result, "matchedCount"), 0);
        if (matchedCount != requestedCount) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_HOT_SKU_NOT_FOUND",
                    "存在不属于当前商品的 SKU，不能设置为热点。",
                    HttpStatus.NOT_FOUND);
        }
        if (toInt(value(result, "inactiveCount"), 0) > 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_HOT_SKU_INACTIVE",
                    "只有启用状态的 SKU 可以设置为热点。",
                    HttpStatus.BAD_REQUEST);
        }
        if (toInt(value(result, "stockInvalidCount"), 0) > 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_HOT_SKU_STOCK_INVALID",
                    "热点库存必须大于 0，且不能超过 SKU 当前库存。",
                    HttpStatus.BAD_REQUEST);
        }
        if (toInt(value(result, "affectedCount"), 0) != requestedCount) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_HOT_SKU_SAVE_FAILED",
                    "热点 SKU 保存失败，请刷新后重试。",
                    HttpStatus.CONFLICT);
        }
    }

    private void writeHotSkusToRedis(Long spuId, List<RedisHotSkuItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        try {
            stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings({"rawtypes", "unchecked"})
                public Object execute(RedisOperations operations) {
                    for (RedisHotSkuItem item : items) {
                        String skuId = ProductSkuIdCodec.hexToBase62(item.skuId());
                        String metaKey = hotSkuMetaKey(skuId);
                        String stockKey = hotSkuStockKey(skuId);
                        Map<String, String> meta = new LinkedHashMap<>();
                        meta.put("spuId", String.valueOf(spuId));
                        meta.put("skuId", skuId);
                        meta.put("status", normalizeHotStatus(item.status()));
                        meta.put("startAtEpochMs", nullableLongText(item.startAtEpochMs()));
                        meta.put("endAtEpochMs", nullableLongText(item.endAtEpochMs()));
                        meta.put("stockQuantity", String.valueOf(item.stockQuantity()));
                        meta.put("version", String.valueOf(item.version()));
                        operations.opsForHash().putAll(metaKey, meta);
                        operations.persist(metaKey);
                        operations.opsForValue().set(stockKey, String.valueOf(item.remainingQuantity()));
                        operations.persist(stockKey);
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("[Product Hot SKU] Redis hot SKU write failed, count={}", items.size(), e);
        }
    }

    private void deleteHotSkusFromRedis(List<String> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>(skuIds.size() * 2);
        for (String skuId : skuIds) {
            keys.add(hotSkuMetaKey(skuId));
            keys.add(hotSkuStockKey(skuId));
        }
        try {
            stringRedisTemplate.delete(keys);
        } catch (Exception e) {
            log.warn("[Product Hot SKU] Redis hot SKU delete failed, count={}", skuIds.size(), e);
        }
    }

    private List<RedisHotSkuItem> parseRedisItems(Object raw) {
        String json = normalizeText(raw);
        if (json.isEmpty() || "[]".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_HOT_SKU_REDIS_PAYLOAD_INVALID",
                    "热点 SKU Redis 预热数据无效。",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<String> normalizeBatchSkuIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_HOT_SKU_BATCH_EMPTY",
                    "请选择需要删除热点的 SKU。",
                    HttpStatus.BAD_REQUEST);
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            ids.add(normalizeSkuId(rawId));
        }
        return new ArrayList<>(ids);
    }

    private List<byte[]> skuIdBytes(List<String> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return skuIds.stream()
                .map(this::skuIdBytes)
                .toList();
    }

    private byte[] skuIdBytes(String skuId) {
        try {
            return ProductSkuIdCodec.fromBase62(skuId);
        } catch (IllegalArgumentException e) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_ID_INVALID",
                    "SKU ID 无效。",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeSkuId(String rawSkuId) {
        String value = normalizeText(rawSkuId);
        if (!value.matches(ProductSkuIdCodec.BASE62_PATTERN)) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_SKU_ID_INVALID",
                    "SKU ID 无效。",
                    HttpStatus.BAD_REQUEST);
        }
        skuIdBytes(value);
        return value;
    }

    private int normalizeStockQuantity(Integer rawStockQuantity) {
        if (rawStockQuantity == null || rawStockQuantity <= 0) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_HOT_SKU_STOCK_INVALID",
                    "热点库存必须大于 0。",
                    HttpStatus.BAD_REQUEST);
        }
        return rawStockQuantity;
    }

    private String normalizeHotStatus(String rawStatus) {
        String status = normalizeText(rawStatus);
        if (status.isEmpty()) {
            status = STATUS_ENABLED;
        }
        status = status.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STATUS.contains(status)) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_HOT_SKU_STATUS_INVALID",
                    "热点状态只能是 ENABLED、DISABLED 或 SOLD_OUT。",
                    HttpStatus.BAD_REQUEST);
        }
        return status;
    }

    private OffsetDateTime parseOptionalDateTime(String rawValue, String label) {
        String value = normalizeText(rawValue);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toOffsetDateTime();
            } catch (DateTimeParseException e) {
                throw new AdminServiceException(
                        "ADMIN_PRODUCT_HOT_SKU_TIME_INVALID",
                        label + "格式无效。",
                        HttpStatus.BAD_REQUEST);
            }
        }
    }

    private Long toEpochMillis(OffsetDateTime value) {
        return value == null ? null : value.toInstant().toEpochMilli();
    }

    private AdminProductHotSkuResponse toResponse(Map<String, Object> row) {
        return new AdminProductHotSkuResponse(
                toHotIdText(value(row, "id")),
                toLong(value(row, "spuId"), 0L),
                toHotIdText(value(row, "skuId")),
                normalizeText(value(row, "skuCode")),
                normalizeText(value(row, "skuName")),
                toInt(value(row, "skuStockQuantity"), 0),
                normalizeText(value(row, "skuStatus")),
                toInt(value(row, "stockQuantity"), 0),
                toInt(value(row, "remainingQuantity"), 0),
                normalizeText(value(row, "status")),
                toOffsetDateTime(value(row, "startAt")),
                toOffsetDateTime(value(row, "endAt")),
                toLong(value(row, "version"), 0L),
                toOffsetDateTime(value(row, "createdAt")),
                toOffsetDateTime(value(row, "updatedAt")));
    }

    private OffsetDateTime toOffsetDateTime(Object raw) {
        if (raw instanceof OffsetDateTime value) {
            return value;
        }
        if (raw instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (raw instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        String text = normalizeText(raw);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.parse(text).atZone(ZoneId.systemDefault()).toOffsetDateTime();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    private String toHotIdText(Object raw) {
        return ProductSkuIdCodec.toBase62FromDatabaseValue(raw);
    }

    private Long normalizeRequiredId(Long id, String label) {
        if (id == null || id <= 0L) {
            throw new AdminServiceException(
                    "ADMIN_PRODUCT_ID_INVALID",
                    label + "无效。",
                    HttpStatus.BAD_REQUEST);
        }
        return id;
    }

    private AdminServiceException productNotFoundException() {
        return new AdminServiceException(
                "ADMIN_PRODUCT_NOT_FOUND",
                "商品不存在。",
                HttpStatus.NOT_FOUND);
    }

    private Object value(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String snakeKey = toSnakeCase(key);
        return row.get(snakeKey);
    }

    private String toSnakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index += 1) {
            char ch = value.charAt(index);
            if (Character.isUpperCase(ch)) {
                builder.append('_').append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = normalizeText(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = normalizeText(value);
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long toLong(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = normalizeText(value);
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String normalizeText(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private String nullableLongText(Long value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String hotSkuMetaKey(String skuId) {
        return HOT_SKU_META_KEY_PREFIX + skuId;
    }

    private String hotSkuStockKey(String skuId) {
        return HOT_SKU_STOCK_KEY_PREFIX + skuId;
    }

    private void runAfterCommit(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    private record NormalizedHotSkuEnableItem(String hotIdBytesHex,
                                              String skuId,
                                              String skuIdBytesHex,
                                              int stockQuantity,
                                              String status,
                                              Long startAtEpochMs,
                                              Long endAtEpochMs) {
    }

    private record RedisHotSkuItem(String skuId,
                                   int stockQuantity,
                                   int remainingQuantity,
                                   String status,
                                   Long startAtEpochMs,
                                   Long endAtEpochMs,
                                   long version) {
    }
}
