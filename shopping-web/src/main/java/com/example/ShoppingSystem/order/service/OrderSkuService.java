package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class OrderSkuService {

    private final OrderMapper orderMapper;
    private final Cache<String, OrderSkuSnapshot> skuSnapshotCache;

    public OrderSkuService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
        this.skuSnapshotCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(5))
                .build();
    }

    public OrderSkuSnapshot loadActiveSku(String rawSkuId, OffsetDateTime now) {
        byte[] skuId = parseSkuId(rawSkuId);
        String skuIdText = HybridIdCodec.toBase62(skuId);
        return skuSnapshotCache.get(skuIdText, ignored -> loadActiveSkuFromDb(skuId, now));
    }

    private OrderSkuSnapshot loadActiveSkuFromDb(byte[] skuId, OffsetDateTime now) {
        Map<String, Object> row = orderMapper.findSkuForOrder(skuId);
        if (row == null || row.isEmpty()) {
            throw new OrderServiceException("ORDER_SKU_INVALID", "SKU does not exist.", HttpStatus.BAD_REQUEST);
        }
        String skuStatus = OrderRowMapper.text(row, "skuStatus");
        String spuStatus = OrderRowMapper.text(row, "spuStatus");
        String categoryStatus = OrderRowMapper.text(row, "categoryStatus");
        if (!"ACTIVE".equals(skuStatus) || !"ACTIVE".equals(spuStatus) || !"ACTIVE".equals(categoryStatus)) {
            throw new OrderServiceException("ORDER_SKU_DISABLED", "SKU is not available.", HttpStatus.CONFLICT);
        }
        boolean hotSku = orderMapper.findHotSkuForOrder(skuId, now) != null;
        return new OrderSkuSnapshot(
                skuId,
                HybridIdCodec.toBase62(skuId),
                OrderRowMapper.longValue(row, "spuId"),
                OrderRowMapper.longValue(row, "categoryId"),
                OrderRowMapper.text(row, "skuCode"),
                OrderRowMapper.text(row, "skuName"),
                OrderRowMapper.text(row, "specJson"),
                OrderRowMapper.text(row, "skuImageUrl"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "priceYuan")),
                hotSku
        );
    }

    private byte[] parseSkuId(String rawSkuId) {
        String value = rawSkuId == null ? "" : rawSkuId.trim();
        if (!value.matches(HybridIdCodec.BASE62_PATTERN)) {
            throw new OrderServiceException("ORDER_SKU_INVALID", "SKU id is invalid.", HttpStatus.BAD_REQUEST);
        }
        try {
            return HybridIdCodec.fromBase62(value);
        } catch (IllegalArgumentException e) {
            throw new OrderServiceException("ORDER_SKU_INVALID", "SKU id is invalid.", HttpStatus.BAD_REQUEST);
        }
    }
}
