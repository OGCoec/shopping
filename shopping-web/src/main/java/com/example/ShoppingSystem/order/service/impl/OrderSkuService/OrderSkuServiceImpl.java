package com.example.ShoppingSystem.order.service.impl.OrderSkuService;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.mapper.product.OrderProductSkuMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import com.example.ShoppingSystem.order.service.OrderSkuService;
import com.example.ShoppingSystem.order.service.OrderAmountCalculator;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderSkuSnapshot;
@Service
public class OrderSkuServiceImpl implements OrderSkuService {

    private final OrderProductSkuMapper orderProductSkuMapper;
    private final Cache<String, OrderSkuSnapshot> skuSnapshotCache;

    public OrderSkuServiceImpl(OrderProductSkuMapper orderProductSkuMapper) {
        this.orderProductSkuMapper = orderProductSkuMapper;
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
        Map<String, Object> row = orderProductSkuMapper.findSkuForOrder(skuId);
        if (row == null || row.isEmpty()) {
            throw new OrderServiceException("ORDER_SKU_INVALID", "SKU does not exist.", HttpStatus.BAD_REQUEST);
        }
        String skuStatus = OrderRowMapper.text(row, "skuStatus");
        String spuStatus = OrderRowMapper.text(row, "spuStatus");
        String categoryStatus = OrderRowMapper.text(row, "categoryStatus");
        if (!"ACTIVE".equals(skuStatus) || !"ACTIVE".equals(spuStatus) || !"ACTIVE".equals(categoryStatus)) {
            throw new OrderServiceException("ORDER_SKU_DISABLED", "SKU is not available.", HttpStatus.CONFLICT);
        }
        boolean hotSku = orderProductSkuMapper.findHotSkuForOrder(skuId, now) != null;
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
                OrderRowMapper.boolValue(row, "pointExchangeEnabled"),
                nonNegativeLong(OrderRowMapper.longValue(row, "pointExchangePoints")),
                hotSku
        );
    }

    private long nonNegativeLong(Long value) {
        return value == null || value < 0L ? 0L : value;
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
