package com.example.ShoppingSystem.order.service.impl.OrderCreateService;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.dto.OrderCreateRequest;
import com.example.ShoppingSystem.order.dto.OrderCreateResponse;
import com.example.ShoppingSystem.order.rabbit.OrderExpireMessagePublisher;
import com.example.ShoppingSystem.order.rabbit.OrderExpireRabbitProperties;
import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryDeductResult;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryStrategy;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.ShoppingSystem.order.service.OrderCreateService;
import com.example.ShoppingSystem.order.service.LockedOrderCoupon;
import com.example.ShoppingSystem.order.service.OrderAmountCalculator;
import com.example.ShoppingSystem.order.service.OrderCouponService;
import com.example.ShoppingSystem.order.service.OrderCouponUsageService;
import com.example.ShoppingSystem.order.service.OrderCreateContext;
import com.example.ShoppingSystem.order.service.OrderInventoryItem;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderSkuService;
import com.example.ShoppingSystem.order.service.OrderSkuSnapshot;
import com.example.ShoppingSystem.order.service.OrderStatus;
@Service
public class OrderCreateServiceImpl implements OrderCreateService {

    private static final Logger log = LoggerFactory.getLogger(OrderCreateService.class);

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(10);

    private final OrderMapper orderMapper;
    private final OrderSkuService orderSkuService;
    private final OrderCouponService orderCouponService;
    private final OrderCouponUsageService orderCouponUsageService;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final RoutedTransactionExecutor routedTransactionExecutor;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final StringRedisTemplate stringRedisTemplate;
    private final OrderExpireMessagePublisher orderExpireMessagePublisher;
    private final OrderExpireRabbitProperties orderExpireRabbitProperties;
    private final Map<OrderInventoryType, OrderInventoryStrategy> inventoryStrategies;
    private final boolean orderLoadtestBypassGuards;

    public OrderCreateServiceImpl(OrderMapper orderMapper,
                              OrderSkuService orderSkuService,
                              OrderCouponService orderCouponService,
                              OrderCouponUsageService orderCouponUsageService,
                              HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                              RoutedTransactionExecutor routedTransactionExecutor,
                              OrderRedisSnapshotService orderRedisSnapshotService,
                              StringRedisTemplate stringRedisTemplate,
                              OrderExpireMessagePublisher orderExpireMessagePublisher,
                              OrderExpireRabbitProperties orderExpireRabbitProperties,
                              List<OrderInventoryStrategy> strategies,
                              @Value("${app.order.loadtest.bypass-guards:false}") boolean orderLoadtestBypassGuards) {
        this.orderMapper = orderMapper;
        this.orderSkuService = orderSkuService;
        this.orderCouponService = orderCouponService;
        this.orderCouponUsageService = orderCouponUsageService;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.routedTransactionExecutor = routedTransactionExecutor;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.orderExpireMessagePublisher = orderExpireMessagePublisher;
        this.orderExpireRabbitProperties = orderExpireRabbitProperties;
        this.orderLoadtestBypassGuards = orderLoadtestBypassGuards;
        this.inventoryStrategies = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(OrderInventoryStrategy::type, Function.identity()));
    }

    public OrderCreateResponse create(Long userId, OrderCreateRequest request) {
        String rawIdempotencyKey = normalizeIdempotencyKey(request == null ? null : request.idempotencyKey());
        String idempotencyKey = userId + ":" + rawIdempotencyKey;
        String redisIdempotencyKey = OrderRedisKeys.idempotencyKey(userId, rawIdempotencyKey);
        OrderCreateResponse existing = existingByIdempotency(userId, idempotencyKey, redisIdempotencyKey);
        if (existing != null) {
            return existing;
        }

        OffsetDateTime now = OffsetDateTime.now();
        int quantity = normalizeQuantity(request == null ? null : request.quantity());
        OrderSkuSnapshot sku = orderSkuService.loadActiveSku(request == null ? null : request.skuId(), now);
        OrderInventoryType inventoryType = sku.hotSku() ? OrderInventoryType.HOT : OrderInventoryType.NORMAL;
        OrderInventoryStrategy inventoryStrategy = inventoryStrategies.get(inventoryType);
        if (inventoryStrategy == null) {
            throw new OrderServiceException("ORDER_INVENTORY_STRATEGY_NOT_FOUND", "Order inventory strategy is missing.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        byte[] orderNoBytes = hybridSemaphoreIdWorker.nextId();
        String orderNo = HybridIdCodec.toBase62(orderNoBytes);
        OffsetDateTime expireAt = now.plus(Duration.ofMillis(paymentCheckWindowMillis()));
        if (!acquireIdempotency(redisIdempotencyKey, orderNo)) {
            String oldOrderNo = stringRedisTemplate.opsForValue().get(redisIdempotencyKey);
            OrderCreateResponse replay = oldOrderNo == null
                    ? existingByIdempotency(userId, idempotencyKey, redisIdempotencyKey)
                    : existingByOrderNo(userId, oldOrderNo);
            if (replay != null) {
                return replay;
            }
            throw new OrderServiceException("ORDER_CREATE_PENDING", "Order creation is pending.", HttpStatus.CONFLICT);
        }

        OrderCreateContext context = new OrderCreateContext(
                orderNo,
                userId,
                sku,
                quantity,
                idempotencyKey,
                request == null ? null : request.userCouponId(),
                now,
                expireAt,
                inventoryType
        );
        OrderCreateDraft draft = null;
        boolean inventoryDeducted = false;
        try {
            OrderInventoryDeductResult deductResult = routedTransactionExecutor.execute(
                    DataSourceRoute.PRODUCT,
                    () -> inventoryStrategy.deduct(context)
            );
            if (!deductResult.success()) {
                throw new OrderServiceException(deductResult.code(), deductResult.message(), HttpStatus.CONFLICT);
            }
            inventoryDeducted = true;
            draft = routedTransactionExecutor.execute(DataSourceRoute.TRADE, () -> {
                BigDecimal totalAmount = OrderAmountCalculator.lineAmount(sku.priceYuan(), quantity);
                long requiredPoints = requiredPoints(sku, quantity);
                LockedOrderCoupon lockedCoupon = orderCouponService.lockCoupon(
                        userId,
                        sku,
                        totalAmount,
                        request == null ? null : request.userCouponId(),
                        orderNo,
                        now
                );
                BigDecimal discountAmount = OrderAmountCalculator.discount(totalAmount, lockedCoupon);
                BigDecimal payAmount = OrderAmountCalculator.money(totalAmount.subtract(discountAmount));
                orderCouponUsageService.writeLock(userId, lockedCoupon, totalAmount, discountAmount, orderNo);
                return new OrderCreateDraft(lockedCoupon, totalAmount, discountAmount, payAmount, requiredPoints);
            });
            orderRedisSnapshotService.saveCreatedOrder(
                    context,
                    draft.lockedCoupon(),
                    draft.totalAmount(),
                    draft.discountAmount(),
                    draft.payAmount(),
                    draft.requiredPoints()
            );
            publishExpireMessage(orderNo, userId, expireAt);
            return new OrderCreateResponse(orderNo, OrderStatus.PENDING_PAYMENT, expireAt, draft.payAmount());
        } catch (RuntimeException e) {
            stringRedisTemplate.delete(redisIdempotencyKey);
            try {
                compensateCreatedResources(inventoryStrategy, context, draft == null ? null : draft.lockedCoupon(), inventoryDeducted);
            } catch (RuntimeException compensationError) {
                e.addSuppressed(compensationError);
                log.warn("[Order] create compensation failed, orderNo={}", orderNo, compensationError);
            }
            throw e;
        }
    }

    private boolean acquireIdempotency(String redisKey, String orderNo) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, orderNo, IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    private OrderCreateResponse existingByIdempotency(Long userId, String idempotencyKey, String redisIdempotencyKey) {
        String orderNo = stringRedisTemplate.opsForValue().get(redisIdempotencyKey);
        if (orderNo != null && !orderNo.isBlank()) {
            OrderCreateResponse response = existingByOrderNo(userId, orderNo);
            if (response != null) {
                return response;
            }
        }
        if (orderLoadtestBypassGuards) {
            return null;
        }
        Map<String, Object> row = orderMapper.findOrderByIdempotencyKey(idempotencyKey, userId);
        return toCreateResponse(row);
    }

    private OrderCreateResponse existingByOrderNo(Long userId, String orderNo) {
        OrderCreateResponse redisResponse = orderRedisSnapshotService.findCreateResponse(userId, orderNo).orElse(null);
        if (redisResponse != null) {
            return redisResponse;
        }
        Map<String, Object> row = orderMapper.findOrderByOrderNoForUser(orderNo, userId);
        return toCreateResponse(row);
    }

    private OrderCreateResponse toCreateResponse(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        return new OrderCreateResponse(
                OrderRowMapper.text(row, "orderNo"),
                OrderRowMapper.text(row, "status"),
                OrderRowMapper.offsetDateTime(row, "expireAt"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "payAmountYuan"))
        );
    }

    private void compensateCreatedResources(OrderInventoryStrategy inventoryStrategy,
                                            OrderCreateContext context,
                                            LockedOrderCoupon lockedCoupon,
                                            boolean inventoryDeducted) {
        if (inventoryDeducted) {
            routedTransactionExecutor.executeWithoutResult(DataSourceRoute.PRODUCT, () ->
                    inventoryStrategy.release(new OrderInventoryItem(
                            context.orderNo(),
                            context.userId(),
                            context.sku().skuId(),
                            context.sku().skuIdText(),
                            context.quantity(),
                            context.sku().hotSku()
                    ))
            );
        }
        if (lockedCoupon != null) {
            routedTransactionExecutor.executeWithoutResult(DataSourceRoute.TRADE, () -> {
                LockedOrderCoupon releasedCoupon = orderCouponService.releaseLockedCoupon(context.orderNo(), OffsetDateTime.now());
                orderCouponUsageService.writeRelease(
                        context.userId(),
                        releasedCoupon == null ? lockedCoupon : releasedCoupon,
                        context.orderNo()
                );
            });
        }
    }

    private void publishExpireMessage(String orderNo, Long userId, OffsetDateTime expireAt) {
        try {
            orderExpireMessagePublisher.publishPaymentCheck(
                    orderNo,
                    userId,
                    expireAt.toInstant().toEpochMilli()
            );
        } catch (Exception e) {
            log.warn("[Order] expire message publish failed, orderNo={}", orderNo, e);
        }
    }

    private long paymentCheckWindowMillis() {
        return Math.max(1L, orderExpireRabbitProperties.paymentCheckWindowMillis());
    }

    private int normalizeQuantity(Integer rawQuantity) {
        if (rawQuantity == null || rawQuantity <= 0) {
            throw new OrderServiceException("ORDER_QUANTITY_INVALID", "Quantity is invalid.", HttpStatus.BAD_REQUEST);
        }
        return rawQuantity;
    }

    private long requiredPoints(OrderSkuSnapshot sku, int quantity) {
        if (sku == null || !sku.pointExchangeEnabled()) {
            return 0L;
        }
        Long points = sku.pointExchangePoints();
        return (points == null || points < 0L ? 0L : points) * Math.max(0, quantity);
    }

    private String normalizeIdempotencyKey(String rawKey) {
        String value = rawKey == null ? "" : rawKey.trim();
        if (value.isEmpty() || value.length() > 96) {
            throw new OrderServiceException("ORDER_IDEMPOTENCY_KEY_INVALID", "Idempotency key is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private record OrderCreateDraft(LockedOrderCoupon lockedCoupon,
                                    BigDecimal totalAmount,
                                    BigDecimal discountAmount,
                                    BigDecimal payAmount,
                                    long requiredPoints) {
    }
}
