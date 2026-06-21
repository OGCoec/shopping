package com.example.ShoppingSystem.order.rabbit;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import com.example.ShoppingSystem.order.service.LockedOrderCoupon;
import com.example.ShoppingSystem.order.service.OrderAmountCalculator;
import com.example.ShoppingSystem.order.service.OrderCouponService;
import com.example.ShoppingSystem.order.service.OrderCouponUsageService;
import com.example.ShoppingSystem.order.service.OrderCreateContext;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderSkuSnapshot;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryType;
import com.example.ShoppingSystem.outbox.annotation.IdempotentConsumer;
import com.example.ShoppingSystem.outbox.orderstock.OrderStockDeductResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Component
public class OrderStockDeductResultTradeConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderStockDeductResultTradeConsumer.class);

    private final OrderMapper orderMapper;
    private final OrderCouponService orderCouponService;
    private final OrderCouponUsageService orderCouponUsageService;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderExpireMessagePublisher orderExpireMessagePublisher;
    private final StringRedisTemplate stringRedisTemplate;

    public OrderStockDeductResultTradeConsumer(OrderMapper orderMapper,
                                               OrderCouponService orderCouponService,
                                               OrderCouponUsageService orderCouponUsageService,
                                               OrderRedisSnapshotService orderRedisSnapshotService,
                                               OrderExpireMessagePublisher orderExpireMessagePublisher,
                                               StringRedisTemplate stringRedisTemplate) {
        this.orderMapper = orderMapper;
        this.orderCouponService = orderCouponService;
        this.orderCouponUsageService = orderCouponUsageService;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.orderExpireMessagePublisher = orderExpireMessagePublisher;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @RabbitListener(
            queues = "#{orderStockDeductResultTradeQueue.name}",
            containerFactory = "orderStockDeductResultRabbitListenerContainerFactory"
    )
    @IdempotentConsumer(route = DataSourceRoute.TRADE, consumer = "order-stock-deduct-result-trade",
            eventId = "#message.eventId", transactional = true)
    public void consume(OrderStockDeductResultMessage message) {
        if (!isUsable(message)) {
            log.warn("[OrderStockDeductResult] invalid message skipped, message={}", message);
            return;
        }
        OffsetDateTime now = occurredAt(message.getOccurredAtEpochMillis());
        if (message.isSuccess()) {
            confirmStockDeducted(message, now);
        } else {
            cancelStockConfirming(message, now);
        }
    }

    private void confirmStockDeducted(OrderStockDeductResultMessage message, OffsetDateTime now) {
        Map<String, Object> order = orderMapper.confirmStockDeducted(message.getOrderNo(), message.getUserId(), now);
        if (order == null || order.isEmpty()) {
            log.info("[OrderStockDeductResult] stock success ignored, orderNo={}, eventId={}",
                    message.getOrderNo(), message.getEventId());
            return;
        }
        List<Map<String, Object>> items = orderMapper.listOrderItems(message.getOrderNo());
        if (items == null || items.isEmpty()) {
            throw new OrderServiceException("ORDER_ITEM_MISSING", "Order item is missing.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        OrderCreateContext context = createContext(order, items.get(0), now);
        LockedOrderCoupon lockedCoupon = lockedCoupon(order);
        BigDecimal totalAmount = OrderAmountCalculator.money(OrderRowMapper.decimal(order, "totalAmountYuan"));
        BigDecimal discountAmount = OrderAmountCalculator.money(OrderRowMapper.decimal(order, "discountAmountYuan"));
        BigDecimal payAmount = OrderAmountCalculator.money(OrderRowMapper.decimal(order, "payAmountYuan"));
        long requiredPoints = Math.max(0L, valueOrZero(OrderRowMapper.longValue(order, "requiredPoints")));
        afterCommit(() -> {
            saveRedisSnapshot(context, lockedCoupon, totalAmount, discountAmount, payAmount, requiredPoints);
            publishPaymentCheck(context);
        });
        log.info("[OrderStockDeductResult] trade order confirmed, orderNo={}, eventId={}",
                message.getOrderNo(), message.getEventId());
    }

    private void cancelStockConfirming(OrderStockDeductResultMessage message, OffsetDateTime now) {
        Map<String, Object> order = orderMapper.cancelStockConfirming(message.getOrderNo(), message.getUserId(), now);
        if (order == null || order.isEmpty()) {
            log.info("[OrderStockDeductResult] stock failure ignored, orderNo={}, code={}, eventId={}",
                    message.getOrderNo(), message.getCode(), message.getEventId());
            return;
        }
        if (hasUserCoupon(order)) {
            LockedOrderCoupon releasedCoupon = orderCouponService.releaseLockedCoupon(message.getOrderNo(), now);
            orderCouponUsageService.writeRelease(message.getUserId(), releasedCoupon, message.getOrderNo());
        }
        String redisIdempotencyKey = redisIdempotencyKey(order, message.getUserId());
        if (!redisIdempotencyKey.isBlank()) {
            afterCommit(() -> stringRedisTemplate.delete(redisIdempotencyKey));
        }
        log.info("[OrderStockDeductResult] trade order cancelled, orderNo={}, code={}, eventId={}",
                message.getOrderNo(), message.getCode(), message.getEventId());
    }

    private OrderCreateContext createContext(Map<String, Object> order,
                                             Map<String, Object> item,
                                             OffsetDateTime fallbackNow) {
        boolean hotSku = OrderRowMapper.boolValue(item, "hotSku");
        OrderSkuSnapshot sku = new OrderSkuSnapshot(
                OrderRowMapper.idBytes(item, "skuId"),
                OrderRowMapper.idText(item, "skuId"),
                OrderRowMapper.longValue(item, "spuId"),
                null,
                OrderRowMapper.text(item, "skuCode"),
                OrderRowMapper.text(item, "skuName"),
                OrderRowMapper.text(item, "specJson"),
                OrderRowMapper.text(item, "skuImageUrl"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(item, "salePriceYuan")),
                OrderRowMapper.boolValue(item, "pointExchangeEnabled"),
                OrderRowMapper.longValue(item, "pointExchangePoints"),
                hotSku
        );
        OffsetDateTime createdAt = nonNullTime(OrderRowMapper.offsetDateTime(order, "createdAt"), fallbackNow);
        OffsetDateTime expireAt = nonNullTime(OrderRowMapper.offsetDateTime(order, "expireAt"), fallbackNow);
        return new OrderCreateContext(
                OrderRowMapper.text(order, "orderNo"),
                OrderRowMapper.longValue(order, "userId"),
                sku,
                OrderRowMapper.intValue(item, "quantity", 0),
                OrderRowMapper.text(order, "idempotencyKey"),
                OrderRowMapper.idText(order, "userCouponId"),
                createdAt,
                expireAt,
                hotSku ? OrderInventoryType.HOT : OrderInventoryType.NORMAL
        );
    }

    private LockedOrderCoupon lockedCoupon(Map<String, Object> order) {
        if (!hasUserCoupon(order)) {
            return null;
        }
        return new LockedOrderCoupon(
                OrderRowMapper.idBytes(order, "userCouponId"),
                OrderRowMapper.idText(order, "userCouponId"),
                null,
                "",
                "",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private boolean hasUserCoupon(Map<String, Object> order) {
        return !OrderRowMapper.idText(order, "userCouponId").isBlank()
                || !OrderRowMapper.text(order, "userCouponIdHex").isBlank();
    }

    private String redisIdempotencyKey(Map<String, Object> order, Long userId) {
        String stored = OrderRowMapper.text(order, "idempotencyKey");
        if (stored.isBlank() || userId == null) {
            return "";
        }
        String prefix = userId + ":";
        String rawKey = stored.startsWith(prefix) ? stored.substring(prefix.length()) : stored;
        return rawKey.isBlank() ? "" : OrderRedisKeys.idempotencyKey(userId, rawKey);
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
            log.warn("[OrderStockDeductResult] Redis snapshot save failed, orderNo={}", context.orderNo(), e);
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
            log.warn("[OrderStockDeductResult] payment check publish failed, orderNo={}", context.orderNo(), e);
        }
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

    private OffsetDateTime occurredAt(long epochMillis) {
        long millis = epochMillis <= 0L ? System.currentTimeMillis() : epochMillis;
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }

    private OffsetDateTime nonNullTime(OffsetDateTime value, OffsetDateTime fallback) {
        return value == null ? fallback : value;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private boolean isUsable(OrderStockDeductResultMessage message) {
        return message != null
                && message.getEventId() != null && !message.getEventId().isBlank()
                && message.getOrderNo() != null && !message.getOrderNo().isBlank()
                && message.getUserId() != null;
    }
}