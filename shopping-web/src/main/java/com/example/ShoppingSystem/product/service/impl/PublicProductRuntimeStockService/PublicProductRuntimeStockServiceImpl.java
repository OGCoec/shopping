package com.example.ShoppingSystem.product.service.impl.PublicProductRuntimeStockService;

import com.example.ShoppingSystem.Utils.ProductSkuIdCodec;
import com.example.ShoppingSystem.config.datasource.ProductReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.product.ProductHotSkuMapper;
import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import com.example.ShoppingSystem.product.dto.PublicProductDetailResponse;
import com.example.ShoppingSystem.product.dto.PublicProductSkuResponse;
import com.example.ShoppingSystem.product.service.PublicProductRuntimeStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PublicProductRuntimeStockServiceImpl implements PublicProductRuntimeStockService {

    private static final Logger log = LoggerFactory.getLogger(PublicProductRuntimeStockServiceImpl.class);

    private static final String HOT_SKU_STATUS_ENABLED = "ENABLED";

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductHotSkuMapper productHotSkuMapper;
    private final ProductReadReplicaQueryExecutor productReadReplicaQueryExecutor;

    public PublicProductRuntimeStockServiceImpl(StringRedisTemplate stringRedisTemplate,
                                                ProductHotSkuMapper productHotSkuMapper,
                                                ProductReadReplicaQueryExecutor productReadReplicaQueryExecutor) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.productHotSkuMapper = productHotSkuMapper;
        this.productReadReplicaQueryExecutor = productReadReplicaQueryExecutor;
    }

    @Override
    public PublicProductDetailResponse overlayRuntimeStock(PublicProductDetailResponse detail) {
        if (detail == null || detail.skus() == null || detail.skus().isEmpty()) {
            return detail;
        }
        Map<String, byte[]> skuIds = decodeSkuIds(detail.skus());
        Map<String, RuntimeStock> redisStocks = readRedisHotStocks(new ArrayList<>(skuIds.keySet()));
        Map<String, RuntimeStock> dbStocks = readDbRuntimeStocks(detail.id(), unresolvedSkuIds(skuIds, redisStocks));
        List<PublicProductSkuResponse> skus = detail.skus().stream()
                .map(sku -> overlaySku(sku, redisStocks, dbStocks))
                .toList();
        return new PublicProductDetailResponse(
                detail.id(),
                detail.categoryId(),
                detail.categoryName(),
                detail.name(),
                detail.subtitle(),
                detail.brandName(),
                detail.mainImageUrl(),
                detail.imageUrls(),
                detail.detailImageUrls(),
                detail.attributes(),
                detail.description(),
                detail.afterSale(),
                List.copyOf(skus)
        );
    }

    private Map<String, byte[]> decodeSkuIds(List<PublicProductSkuResponse> skus) {
        Map<String, byte[]> ids = new LinkedHashMap<>();
        for (PublicProductSkuResponse sku : skus) {
            String skuId = sku == null || sku.id() == null ? "" : sku.id().trim();
            if (skuId.isEmpty() || ids.containsKey(skuId)) {
                continue;
            }
            try {
                ids.put(skuId, ProductSkuIdCodec.fromBase62(skuId));
            } catch (IllegalArgumentException e) {
                log.warn("[Product public detail] invalid SKU id skipped for runtime stock, skuId={}", skuId);
            }
        }
        return ids;
    }

    private Map<String, RuntimeStock> readRedisHotStocks(List<String> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        List<Object> results;
        try {
            results = stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings({"rawtypes", "unchecked"})
                public Object execute(RedisOperations operations) {
                    for (String skuId : skuIds) {
                        operations.opsForHash().entries(OrderRedisKeys.hotSkuMetaKey(skuId));
                        operations.opsForValue().get(OrderRedisKeys.hotSkuStockKey(skuId));
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("[Product public detail] Redis hot SKU runtime stock read failed, count={}", skuIds.size(), e);
            return Map.of();
        }
        if (results == null || results.isEmpty()) {
            return Map.of();
        }
        long nowEpochMs = System.currentTimeMillis();
        Map<String, RuntimeStock> stocks = new LinkedHashMap<>();
        for (int index = 0; index < skuIds.size(); index += 1) {
            int resultIndex = index * 2;
            if (resultIndex + 1 >= results.size()) {
                break;
            }
            RuntimeStock stock = redisRuntimeStock(results.get(resultIndex), results.get(resultIndex + 1), nowEpochMs);
            if (stock != null) {
                stocks.put(skuIds.get(index), stock);
            }
        }
        return stocks;
    }

    private RuntimeStock redisRuntimeStock(Object rawMeta, Object rawRemaining, long nowEpochMs) {
        if (!(rawMeta instanceof Map<?, ?> meta) || meta.isEmpty()) {
            return null;
        }
        if (!HOT_SKU_STATUS_ENABLED.equalsIgnoreCase(metaText(meta, "status"))) {
            return null;
        }
        if (!activeInWindow(metaText(meta, "startAtEpochMs"), true, nowEpochMs)
                || !activeInWindow(metaText(meta, "endAtEpochMs"), false, nowEpochMs)) {
            return null;
        }
        Integer stockQuantity = positiveInt(metaText(meta, "stockQuantity"));
        Integer remainingQuantity = nonNegativeInt(rawRemaining);
        if (stockQuantity == null || remainingQuantity == null || remainingQuantity > stockQuantity) {
            return null;
        }
        return new RuntimeStock(stockQuantity, remainingQuantity, true);
    }

    private boolean activeInWindow(String rawValue, boolean startBoundary, long nowEpochMs) {
        if (rawValue == null || rawValue.isBlank()) {
            return true;
        }
        try {
            long epochMs = Long.parseLong(rawValue.trim());
            return startBoundary ? epochMs <= nowEpochMs : epochMs > nowEpochMs;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private List<byte[]> unresolvedSkuIds(Map<String, byte[]> allSkuIds, Map<String, RuntimeStock> redisStocks) {
        if (allSkuIds == null || allSkuIds.isEmpty()) {
            return List.of();
        }
        List<byte[]> ids = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : allSkuIds.entrySet()) {
            if (redisStocks == null || !redisStocks.containsKey(entry.getKey())) {
                ids.add(entry.getValue());
            }
        }
        return ids;
    }

    private Map<String, RuntimeStock> readDbRuntimeStocks(Long spuId, List<byte[]> skuIds) {
        if (spuId == null || spuId <= 0L || skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows;
        OffsetDateTime now = OffsetDateTime.now();
        try {
            rows = productReadReplicaQueryExecutor.query(() ->
                    productHotSkuMapper.listRuntimeStocksBySkuIds(spuId, skuIds, now));
        } catch (Exception e) {
            log.warn("[Product public detail] DB runtime stock read failed, spuId={}, count={}", spuId, skuIds.size(), e);
            return Map.of();
        }
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, RuntimeStock> stocks = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String skuId = ProductSkuIdCodec.toBase62FromDatabaseValue(value(row, "skuId"));
            if (skuId.isBlank()) {
                continue;
            }
            int skuStockQuantity = Math.max(0, intValue(value(row, "skuStockQuantity"), 0));
            boolean hotSku = booleanValue(value(row, "hotSku"));
            if (hotSku) {
                Integer hotStockQuantity = positiveInt(value(row, "hotStockQuantity"));
                Integer hotRemainingQuantity = nonNegativeInt(value(row, "hotRemainingQuantity"));
                if (hotStockQuantity != null
                        && hotRemainingQuantity != null
                        && hotRemainingQuantity <= hotStockQuantity) {
                    stocks.put(skuId, new RuntimeStock(hotStockQuantity, hotRemainingQuantity, true));
                    continue;
                }
            }
            stocks.put(skuId, new RuntimeStock(skuStockQuantity, skuStockQuantity, false));
        }
        return stocks;
    }

    private PublicProductSkuResponse overlaySku(PublicProductSkuResponse sku,
                                                Map<String, RuntimeStock> redisStocks,
                                                Map<String, RuntimeStock> dbStocks) {
        if (sku == null) {
            return null;
        }
        String skuId = sku.id() == null ? "" : sku.id().trim();
        RuntimeStock stock = redisStocks.get(skuId);
        if (stock == null) {
            stock = dbStocks.get(skuId);
        }
        if (stock == null) {
            stock = baseStock(sku);
        }
        return new PublicProductSkuResponse(
                sku.id(),
                sku.skuName(),
                sku.specJson(),
                sku.skuImageUrls(),
                sku.priceYuan(),
                sku.originalPriceYuan(),
                stock.stockQuantity(),
                stock.remainingQuantity(),
                stock.hotSku()
        );
    }

    private RuntimeStock baseStock(PublicProductSkuResponse sku) {
        int stockQuantity = Math.max(0, sku.stockQuantity() == null ? 0 : sku.stockQuantity());
        int remainingQuantity = sku.remainingQuantity() == null
                ? stockQuantity
                : Math.max(0, sku.remainingQuantity());
        return new RuntimeStock(stockQuantity, remainingQuantity, Boolean.TRUE.equals(sku.hotSku()));
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

    private String metaText(Map<?, ?> meta, String key) {
        Object value = meta.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer positiveInt(Object value) {
        Integer parsed = nonNegativeInt(value);
        return parsed == null || parsed <= 0 ? null : parsed;
    }

    private Integer nonNegativeInt(Object value) {
        if (value instanceof Number number) {
            int parsed = number.intValue();
            return parsed >= 0 ? parsed : null;
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(text);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int intValue(Object value, int defaultValue) {
        Integer parsed = nonNegativeInt(value);
        return parsed == null ? defaultValue : parsed;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private record RuntimeStock(int stockQuantity, int remainingQuantity, boolean hotSku) {
    }
}
