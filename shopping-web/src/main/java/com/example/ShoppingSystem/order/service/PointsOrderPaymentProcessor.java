package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.dto.OrderPaymentRequest;
import com.example.ShoppingSystem.order.dto.OrderPaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PointsOrderPaymentProcessor implements OrderPaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PointsOrderPaymentProcessor.class);
    private static final String LOADTEST_FAULT_SLEEP_BEFORE_DEDUCT = "SLEEP_BEFORE_DEDUCT";
    private static final String LOADTEST_FAULT_THROW_AFTER_SLEEP = "THROW_AFTER_SLEEP";
    private static final String LOADTEST_FAULT_THROW_AFTER_DEDUCT = "THROW_AFTER_DEDUCT";
    private static final long MAX_LOADTEST_DELAY_MILLIS = 600_000L;
    private static final long ORDER_LOCK_WAIT_MILLIS = 30_000L;
    private static final long ORDER_LOCK_RETRY_INITIAL_MILLIS = 25L;
    private static final long ORDER_LOCK_RETRY_MAX_MILLIS = 250L;

    private final OrderMapper orderMapper;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderRedisPersistScheduler orderRedisPersistScheduler;
    private final OrderCouponService orderCouponService;
    private final OrderCouponUsageService orderCouponUsageService;
    private final OrderCardSecretDeliveryService orderCardSecretDeliveryService;
    private final TransactionTemplate transactionTemplate;
    private final boolean orderLoadtestBypassGuards;
    private final boolean pointsPaymentFaultEnabled;

    public PointsOrderPaymentProcessor(OrderMapper orderMapper,
                                       OrderRedisSnapshotService orderRedisSnapshotService,
                                       OrderRedisPersistScheduler orderRedisPersistScheduler,
                                       OrderCouponService orderCouponService,
                                       OrderCouponUsageService orderCouponUsageService,
                                       OrderCardSecretDeliveryService orderCardSecretDeliveryService,
                                       TransactionTemplate transactionTemplate,
                                       @Value("${app.order.loadtest.bypass-guards:false}") boolean orderLoadtestBypassGuards,
                                       @Value("${app.order.loadtest.points-payment-fault-enabled:false}") boolean pointsPaymentFaultEnabled) {
        this.orderMapper = orderMapper;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.orderRedisPersistScheduler = orderRedisPersistScheduler;
        this.orderCouponService = orderCouponService;
        this.orderCouponUsageService = orderCouponUsageService;
        this.orderCardSecretDeliveryService = orderCardSecretDeliveryService;
        this.transactionTemplate = transactionTemplate;
        this.orderLoadtestBypassGuards = orderLoadtestBypassGuards;
        this.pointsPaymentFaultEnabled = pointsPaymentFaultEnabled;
    }

    @Override
    public String paymentType() {
        return OrderPaymentType.POINTS;
    }

    @Override
    public OrderPaymentResponse pay(Long userId, String orderNo, OrderPaymentRequest request) {
        OffsetDateTime requestReceivedAt = OffsetDateTime.now();
        PointsPaymentResult result = payWithOrderLockRetry(userId, orderNo, request, requestReceivedAt);
        if (result.newlyPaid()) {
            cleanupRedisSnapshot(orderNo, userId);
        }
        return new OrderPaymentResponse(
                orderNo,
                OrderStatus.PAID,
                result.paidAt(),
                null,
                OrderPaymentType.POINTS,
                result.usedPoints(),
                result.availablePoints()
        );
    }

    private PointsPaymentResult payWithOrderLockRetry(Long userId,
                                                      String orderNo,
                                                      OrderPaymentRequest request,
                                                      OffsetDateTime requestReceivedAt) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ORDER_LOCK_WAIT_MILLIS);
        long sleepMillis = ORDER_LOCK_RETRY_INITIAL_MILLIS;
        while (true) {
            PointsPaymentResult result = transactionTemplate.execute(status ->
                    payInTransaction(userId, orderNo, request, requestReceivedAt)
            );
            if (result == null) {
                throw new OrderServiceException("ORDER_PAY_UNAVAILABLE", "Only pending current-user orders can be paid.", HttpStatus.CONFLICT);
            }
            if (!result.orderLockBusy()) {
                return result;
            }
            PointsPaymentResult paid = findExistingPointsPayment(userId, orderNo, requestReceivedAt);
            if (paid != null) {
                return paid;
            }
            if (System.nanoTime() >= deadline) {
                throw new OrderServiceException("ORDER_PAY_IN_PROGRESS", "Order payment is still being processed.", HttpStatus.CONFLICT);
            }
            sleepQuietly(Math.min(sleepMillis, remainingMillis(deadline)));
            sleepMillis = Math.min(ORDER_LOCK_RETRY_MAX_MILLIS, sleepMillis * 2L);
        }
    }

    private PointsPaymentResult findExistingPointsPayment(Long userId, String orderNo, OffsetDateTime fallbackPaidAt) {
        Map<String, Object> order = orderMapper.findOrderByOrderNoForUser(orderNo, userId);
        if (order == null || order.isEmpty()) {
            return null;
        }
        if (!OrderStatus.PAID.equals(OrderRowMapper.text(order, "status"))) {
            return null;
        }
        String paymentType = paymentType(order);
        if (!OrderPaymentType.POINTS.equals(paymentType)) {
            throw new OrderServiceException("ORDER_PAY_UNAVAILABLE", "Order has already been paid by another payment type.", HttpStatus.CONFLICT);
        }
        return PointsPaymentResult.paid(
                existingPaidAt(order, fallbackPaidAt),
                nonNegativeLong(OrderRowMapper.longValue(order, "usedPoints")),
                null,
                false
        );
    }

    private long remainingMillis(long deadlineNanos) {
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
    }

    private void sleepQuietly(long sleepMillis) {
        try {
            Thread.sleep(Math.max(1L, sleepMillis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrderServiceException(
                    "ORDER_PAY_INTERRUPTED",
                    "Order payment wait was interrupted.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private void persistRedisSnapshotIfNeeded(Long userId, String orderNo) {
        Map<String, Object> persisted = orderMapper.findOrderByOrderNoForUser(orderNo, userId);
        if (persisted != null && !persisted.isEmpty()) {
            return;
        }
        if (orderRedisSnapshotService.findSnapshotForUser(orderNo, userId).isEmpty()) {
            return;
        }
        orderRedisPersistScheduler.persistSnapshotNow(orderNo);
    }

    private PointsPaymentResult payInTransaction(Long userId,
                                                 String orderNo,
                                                 OrderPaymentRequest request,
                                                 OffsetDateTime requestReceivedAt) {
        if (!Boolean.TRUE.equals(orderMapper.tryLockOrderState(orderNo))) {
            return PointsPaymentResult.busy();
        }
        persistRedisSnapshotIfNeeded(userId, orderNo);
        Map<String, Object> order = orderMapper.findOrderByOrderNoForUserForUpdate(orderNo, userId);
        if (order == null || order.isEmpty()) {
            throw new OrderServiceException("ORDER_PAY_UNAVAILABLE", "Only pending current-user orders can be paid.", HttpStatus.CONFLICT);
        }
        String status = OrderRowMapper.text(order, "status");
        String paymentType = paymentType(order);
        if (OrderStatus.PAID.equals(status)) {
            if (OrderPaymentType.POINTS.equals(paymentType)) {
                return PointsPaymentResult.paid(
                        existingPaidAt(order, requestReceivedAt),
                        nonNegativeLong(OrderRowMapper.longValue(order, "usedPoints")),
                        null,
                        false
                );
            }
            throw new OrderServiceException("ORDER_PAY_UNAVAILABLE", "Order has already been paid by another payment type.", HttpStatus.CONFLICT);
        }
        if (!OrderStatus.PENDING_PAYMENT.equals(status) && !OrderStatus.CLOSING.equals(status)) {
            throw new OrderServiceException("ORDER_PAY_UNAVAILABLE", "Only pending current-user orders can be paid.", HttpStatus.CONFLICT);
        }
        if (!OrderPaymentType.UNPAID.equals(paymentType)) {
            throw new OrderServiceException("ORDER_PAY_UNAVAILABLE", "Only unpaid orders can be paid with points.", HttpStatus.CONFLICT);
        }
        if (isExpiredForRequest(order, requestReceivedAt)) {
            throw new OrderServiceException("ORDER_PAY_EXPIRED", "Order payment time has expired.", HttpStatus.CONFLICT);
        }

        PointsSummary summary = pointsSummary(order, orderNo);
        if (summary.itemCount() <= 0 || !summary.pointExchangeAvailable() || summary.requiredPoints() <= 0) {
            throw new OrderServiceException(
                    "ORDER_POINTS_PAYMENT_UNAVAILABLE",
                    "Order items do not support points payment.",
                    HttpStatus.CONFLICT
            );
        }

        applyLoadtestFaultBeforeDeduct(request);
        releaseCouponIfNeeded(userId, orderNo, order, requestReceivedAt);
        Map<String, Object> account = orderMapper.deductUserPoints(userId, summary.requiredPoints());
        if (account == null || account.isEmpty()) {
            throw new OrderServiceException("ORDER_POINTS_NOT_ENOUGH", "Available points are not enough.", HttpStatus.CONFLICT);
        }
        applyLoadtestFaultAfterDeduct(request);
        Map<String, Object> paidOrder = orderMapper.markPointsPaidOrderForUser(orderNo, userId, requestReceivedAt, requestReceivedAt, summary.requiredPoints());
        if (paidOrder == null || paidOrder.isEmpty()) {
            throw new OrderServiceException("ORDER_PAY_UNAVAILABLE", "Only pending current-user orders can be paid.", HttpStatus.CONFLICT);
        }
        ensureCardSecretDelivered(orderCardSecretDeliveryService.deliverPaidOrder(orderNo, userId, null));
        return PointsPaymentResult.paid(
                requestReceivedAt,
                summary.requiredPoints(),
                nonNegativeLong(OrderRowMapper.longValue(account, "availablePoints")),
                true
        );
    }

    private void ensureCardSecretDelivered(OrderCardSecretDeliveryService.DeliveryBatchResult delivery) {
        if (delivery == null || delivery.requiredCount() <= 0) {
            throw new OrderServiceException(
                    "ORDER_CARD_SECRET_DELIVERY_UNAVAILABLE",
                    "Order card secret delivery is unavailable.",
                    HttpStatus.CONFLICT
            );
        }
        if (delivery.lockBusy()) {
            throw new OrderServiceException(
                    "ORDER_CARD_SECRET_DELIVERY_BUSY",
                    "Order card secret delivery is busy.",
                    HttpStatus.CONFLICT
            );
        }
        if (delivery.shortageCount() > 0 || delivery.deliveredCount() < delivery.requiredCount()) {
            throw new OrderServiceException(
                    "ORDER_CARD_SECRET_NOT_ENOUGH",
                    "Card secret inventory is not enough.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private PointsSummary pointsSummary(Map<String, Object> order, String orderNo) {
        long requiredPoints = nonNegativeLong(OrderRowMapper.longValue(order, "requiredPoints"));
        if (requiredPoints > 0L) {
            return new PointsSummary(1L, true, requiredPoints);
        }
        return pointsSummaryFromItems(orderNo);
    }

    private PointsSummary pointsSummaryFromItems(String orderNo) {
        Map<String, Object> row = orderMapper.summarizeOrderItemPoints(orderNo);
        if (row == null || row.isEmpty()) {
            return new PointsSummary(0L, false, 0L);
        }
        return new PointsSummary(
                nonNegativeLong(OrderRowMapper.longValue(row, "itemCount")),
                OrderRowMapper.boolValue(row, "pointExchangeAvailable"),
                nonNegativeLong(OrderRowMapper.longValue(row, "requiredPoints"))
        );
    }

    private void applyLoadtestFaultBeforeDeduct(OrderPaymentRequest request) {
        if (!loadtestFaultEnabled(request)) {
            return;
        }
        String fault = normalizedLoadtestFault(request);
        if (LOADTEST_FAULT_SLEEP_BEFORE_DEDUCT.equals(fault)) {
            sleepForLoadtest(request);
            return;
        }
        if (LOADTEST_FAULT_THROW_AFTER_SLEEP.equals(fault)) {
            sleepForLoadtest(request);
            throwLoadtestRollback();
        }
    }

    private void applyLoadtestFaultAfterDeduct(OrderPaymentRequest request) {
        if (!loadtestFaultEnabled(request)) {
            return;
        }
        if (LOADTEST_FAULT_THROW_AFTER_DEDUCT.equals(normalizedLoadtestFault(request))) {
            sleepForLoadtest(request);
            throwLoadtestRollback();
        }
    }

    private boolean loadtestFaultEnabled(OrderPaymentRequest request) {
        return orderLoadtestBypassGuards
                && pointsPaymentFaultEnabled
                && request != null
                && !normalizedLoadtestFault(request).isBlank();
    }

    private String normalizedLoadtestFault(OrderPaymentRequest request) {
        String value = request == null ? "" : request.loadtestFault();
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void sleepForLoadtest(OrderPaymentRequest request) {
        long delayMillis = normalizedLoadtestDelayMillis(request);
        if (delayMillis <= 0L) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrderServiceException(
                    "ORDER_POINTS_PAYMENT_LOADTEST_INTERRUPTED",
                    "Loadtest points payment delay was interrupted.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private long normalizedLoadtestDelayMillis(OrderPaymentRequest request) {
        Long value = request == null ? null : request.loadtestDelayMillis();
        if (value == null || value <= 0L) {
            return 0L;
        }
        return Math.min(value, MAX_LOADTEST_DELAY_MILLIS);
    }

    private void throwLoadtestRollback() {
        throw new OrderServiceException(
                "ORDER_POINTS_PAYMENT_LOADTEST_ROLLBACK",
                "Loadtest points payment rollback fault was triggered.",
                HttpStatus.CONFLICT
        );
    }

    private void releaseCouponIfNeeded(Long userId, String orderNo, Map<String, Object> order, OffsetDateTime paidAt) {
        if (OrderRowMapper.idText(order, "userCouponId").isBlank()) {
            return;
        }
        LockedOrderCoupon releasedCoupon = orderCouponService.releaseLockedCoupon(orderNo, paidAt);
        orderCouponUsageService.writeRelease(userId, releasedCoupon, orderNo);
    }

    private void cleanupRedisSnapshot(String orderNo, Long userId) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderNo", orderNo);
        order.put("userId", userId);
        order.put("status", OrderStatus.PAID);
        try {
            orderRedisSnapshotService.completePersistedAndCleanup(
                    List.of(orderNo),
                    List.of(new OrderRedisSnapshot(order, List.of()))
            );
        } catch (Exception e) {
            log.warn("[Order] points payment Redis snapshot cleanup failed, orderNo={}, userId={}", orderNo, userId, e);
        }
    }

    private String paymentType(Map<String, Object> order) {
        String value = OrderRowMapper.text(order, "paymentType");
        return value.isBlank() ? OrderPaymentType.UNPAID : value;
    }

    private OffsetDateTime existingPaidAt(Map<String, Object> order, OffsetDateTime fallback) {
        OffsetDateTime paidAt = OrderRowMapper.offsetDateTime(order, "paidAt");
        return paidAt == null ? fallback : paidAt;
    }

    private boolean isExpiredForRequest(Map<String, Object> order, OffsetDateTime requestReceivedAt) {
        OffsetDateTime expireAt = OrderRowMapper.offsetDateTime(order, "expireAt");
        return expireAt != null && requestReceivedAt != null && requestReceivedAt.isAfter(expireAt);
    }

    private long nonNegativeLong(Long value) {
        return value == null || value < 0L ? 0L : value;
    }

    private record PointsSummary(long itemCount,
                                 boolean pointExchangeAvailable,
                                 long requiredPoints) {
    }

    private record PointsPaymentResult(OffsetDateTime paidAt,
                                       long usedPoints,
                                       Long availablePoints,
                                       boolean newlyPaid,
                                       boolean orderLockBusy) {

        private static PointsPaymentResult paid(OffsetDateTime paidAt,
                                                long usedPoints,
                                                Long availablePoints,
                                                boolean newlyPaid) {
            return new PointsPaymentResult(paidAt, usedPoints, availablePoints, newlyPaid, false);
        }

        private static PointsPaymentResult busy() {
            return new PointsPaymentResult(null, 0L, null, false, true);
        }
    }
}
