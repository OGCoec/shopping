package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.outbox.annotation.TransactionalOutbox;
import com.example.ShoppingSystem.order.rabbit.OrderExpireMessagePublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OrderCreateTradeWriter {

    private static final Logger log = LoggerFactory.getLogger(OrderCreateTradeWriter.class);

    private final OrderMapper orderMapper;
    private final OrderCouponService orderCouponService;
    private final OrderCouponUsageService orderCouponUsageService;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderExpireMessagePublisher orderExpireMessagePublisher;
    private final ObjectMapper objectMapper;

    public OrderCreateTradeWriter(OrderMapper orderMapper,
                                  OrderCouponService orderCouponService,
                                  OrderCouponUsageService orderCouponUsageService,
                                  OrderRedisSnapshotService orderRedisSnapshotService,
                                  OrderExpireMessagePublisher orderExpireMessagePublisher,
                                  ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.orderCouponService = orderCouponService;
        this.orderCouponUsageService = orderCouponUsageService;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.orderExpireMessagePublisher = orderExpireMessagePublisher;
        this.objectMapper = objectMapper;
    }

    @TransactionalOutbox(DataSourceRoute.TRADE)
    public OrderCreateDraft createPendingPaymentOrder(OrderCreateContext context) {
        if (context == null || context.sku() == null) {
            throw new OrderServiceException("ORDER_CREATE_CONTEXT_INVALID", "Order create context is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        OrderSkuSnapshot sku = context.sku();
        BigDecimal totalAmount = OrderAmountCalculator.lineAmount(sku.priceYuan(), context.quantity());
        long requiredPoints = requiredPoints(sku, context.quantity());
        LockedOrderCoupon lockedCoupon = orderCouponService.lockCoupon(
                context.userId(),
                sku,
                totalAmount,
                context.rawUserCouponId(),
                context.orderNo(),
                context.now()
        );
        BigDecimal discountAmount = OrderAmountCalculator.discount(totalAmount, lockedCoupon);
        BigDecimal payAmount = OrderAmountCalculator.money(totalAmount.subtract(discountAmount));
        orderCouponUsageService.writeLock(context.userId(), lockedCoupon, totalAmount, discountAmount, context.orderNo());

        int inserted = orderMapper.insertOrder(
                context.orderNo(),
                context.userId(),
                OrderStatus.PENDING_PAYMENT,
                totalAmount,
                discountAmount,
                payAmount,
                requiredPoints,
                lockedCoupon == null ? null : lockedCoupon.userCouponId(),
                context.idempotencyKey(),
                context.expireAt(),
                context.now()
        );
        if (inserted != 1) {
            throw new OrderServiceException("ORDER_CREATE_FAILED", "Order create failed.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        orderMapper.insertOrderItems(orderItemsJson(context, totalAmount));
        afterCommit(() -> {
            saveRedisSnapshot(context, lockedCoupon, totalAmount, discountAmount, payAmount, requiredPoints);
            publishPaymentCheck(context);
        });
        return new OrderCreateDraft(lockedCoupon, totalAmount, discountAmount, payAmount, requiredPoints);
    }

    private String orderItemsJson(OrderCreateContext context, BigDecimal lineAmount) {
        OrderSkuSnapshot sku = context.sku();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", context.orderNo());
        row.put("user_id", context.userId());
        row.put("spu_id", sku.spuId());
        row.put("sku_id_hex", HybridIdCodec.toHex(sku.skuId()));
        row.put("sku_code", sku.skuCode());
        row.put("sku_name", sku.skuName());
        row.put("spec_json", sku.specJson());
        row.put("sku_image_url", sku.skuImageUrl());
        row.put("quantity", context.quantity());
        row.put("sale_price_yuan", OrderAmountCalculator.money(sku.priceYuan()));
        row.put("line_amount_yuan", OrderAmountCalculator.money(lineAmount));
        row.put("point_exchange_enabled", sku.pointExchangeEnabled());
        row.put("point_exchange_points", pointExchangePoints(sku));
        row.put("line_points", linePoints(sku, context.quantity()));
        row.put("is_hot_sku", sku.hotSku());
        row.put("created_at_epoch_ms", epochMillis(context.now()));
        try {
            return objectMapper.writeValueAsString(List.of(row));
        } catch (JsonProcessingException e) {
            throw new OrderServiceException("ORDER_ITEM_JSON_INVALID", "Order item json is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private long requiredPoints(OrderSkuSnapshot sku, int quantity) {
        if (sku == null || !sku.pointExchangeEnabled()) {
            return 0L;
        }
        return pointExchangePoints(sku) * Math.max(0, quantity);
    }

    private long pointExchangePoints(OrderSkuSnapshot sku) {
        Long points = sku == null ? null : sku.pointExchangePoints();
        return points == null || points < 0L ? 0L : points;
    }

    private long linePoints(OrderSkuSnapshot sku, int quantity) {
        if (sku == null || !sku.pointExchangeEnabled()) {
            return 0L;
        }
        return pointExchangePoints(sku) * Math.max(0, quantity);
    }

    private long epochMillis(OffsetDateTime value) {
        return (value == null ? OffsetDateTime.now() : value).toInstant().toEpochMilli();
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private void saveRedisSnapshot(OrderCreateContext context,
                                   LockedOrderCoupon lockedCoupon,
                                   BigDecimal totalAmount,
                                   BigDecimal discountAmount,
                                   BigDecimal payAmount,
                                   long requiredPoints) {
        try {
            orderRedisSnapshotService.saveCreatedOrder(
                    context,
                    lockedCoupon,
                    totalAmount,
                    discountAmount,
                    payAmount,
                    requiredPoints
            );
        } catch (Exception e) {
            log.warn("[Order] Redis snapshot save failed after order create, orderNo={}", context.orderNo(), e);
        }
    }

    private void publishPaymentCheck(OrderCreateContext context) {
        try {
            orderExpireMessagePublisher.publishPaymentCheck(
                    context.orderNo(),
                    context.userId(),
                    context.expireAt().toInstant().toEpochMilli()
            );
        } catch (Exception e) {
            log.warn("[Order] payment check publish failed after order create, orderNo={}", context.orderNo(), e);
        }
    }

    public record OrderCreateDraft(LockedOrderCoupon lockedCoupon,
                                   BigDecimal totalAmount,
                                   BigDecimal discountAmount,
                                   BigDecimal payAmount,
                                   long requiredPoints) {
    }
}
