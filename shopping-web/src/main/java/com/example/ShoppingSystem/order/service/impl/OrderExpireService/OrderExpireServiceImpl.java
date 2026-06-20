package com.example.ShoppingSystem.order.service.impl.OrderExpireService;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.rabbit.OrderExpireMessagePublisher;
import com.example.ShoppingSystem.order.rabbit.OrderExpireRabbitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.example.ShoppingSystem.order.service.OrderExpireService;
import com.example.ShoppingSystem.order.service.LockedOrderCoupon;
import com.example.ShoppingSystem.order.service.OrderCouponService;
import com.example.ShoppingSystem.order.service.OrderCouponUsageService;
import com.example.ShoppingSystem.order.service.OrderInventoryReleaseService;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshot;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderRedisStateChangeResult;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderStatus;
@Service
public class OrderExpireServiceImpl implements OrderExpireService {

    private static final Logger log = LoggerFactory.getLogger(OrderExpireService.class);

    private final OrderInventoryReleaseService orderInventoryReleaseService;
    private final OrderCouponService orderCouponService;
    private final OrderCouponUsageService orderCouponUsageService;
    private final OrderMapper orderMapper;
    private final RoutedTransactionExecutor routedTransactionExecutor;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderExpireMessagePublisher orderExpireMessagePublisher;
    private final OrderExpireRabbitProperties orderExpireRabbitProperties;

    public OrderExpireServiceImpl(OrderInventoryReleaseService orderInventoryReleaseService,
                              OrderCouponService orderCouponService,
                              OrderCouponUsageService orderCouponUsageService,
                              OrderMapper orderMapper,
                              RoutedTransactionExecutor routedTransactionExecutor,
                              OrderRedisSnapshotService orderRedisSnapshotService,
                              OrderExpireMessagePublisher orderExpireMessagePublisher,
                              OrderExpireRabbitProperties orderExpireRabbitProperties) {
        this.orderInventoryReleaseService = orderInventoryReleaseService;
        this.orderCouponService = orderCouponService;
        this.orderCouponUsageService = orderCouponUsageService;
        this.orderMapper = orderMapper;
        this.routedTransactionExecutor = routedTransactionExecutor;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.orderExpireMessagePublisher = orderExpireMessagePublisher;
        this.orderExpireRabbitProperties = orderExpireRabbitProperties;
    }

    public boolean startClosing(String orderNo) {
        String normalizedOrderNo = orderNo == null ? "" : orderNo.trim();
        if (normalizedOrderNo.isEmpty()) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime closingDeadline = now.plus(Duration.ofMillis(closingFinalizeWindowMillis()));
        OrderRedisStateChangeResult redisResult = orderRedisSnapshotService.startClosingExpired(
                normalizedOrderNo,
                now,
                closingDeadline
        );
        if (redisResult.changed()) {
            Long userId = OrderRowMapper.longValue(redisResult.order(), "userId");
            publishClosingFinalize(normalizedOrderNo, userId, closingDeadline);
            if (log.isInfoEnabled()) {
                log.info("[Order] pending order entered closing grace, orderNo={}", normalizedOrderNo);
            }
            return true;
        }
        if ("ORDER_EXPIRE_1".equals(redisResult.code())) {
            return startPersistedClosing(normalizedOrderNo, now, closingDeadline);
        }
        return false;
    }

    public boolean finalizeClosing(String orderNo) {
        String normalizedOrderNo = orderNo == null ? "" : orderNo.trim();
        if (normalizedOrderNo.isEmpty()) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        FinalizeClosingResult result = routedTransactionExecutor.execute(
                DataSourceRoute.TRADE,
                () -> finalizeClosingInTransaction(normalizedOrderNo, now)
        );
        if (result == null) {
            return false;
        }
        releaseResourcesIfNeeded(result, normalizedOrderNo, now);
        if (result.retry()) {
            publishClosingFinalize(
                    normalizedOrderNo,
                    result.userId(),
                    now.plus(Duration.ofMillis(closingFinalizeWindowMillis()))
            );
            return false;
        }
        if (result.changed()) {
            if (log.isInfoEnabled()) {
                log.info("[Order] finalized closing order, orderNo={}", normalizedOrderNo);
            }
            return true;
        }
        return false;
    }

    private boolean startPersistedClosing(String orderNo, OffsetDateTime now, OffsetDateTime closingDeadline) {
        Map<String, Object> row = routedTransactionExecutor.execute(
                DataSourceRoute.TRADE,
                () -> orderMapper.startClosingExpiredOrder(
                        orderNo,
                        now,
                        closingDeadline
                )
        );
        if (row == null || row.isEmpty()) {
            log.warn("[Order] pending order cannot enter closing because Redis snapshot is missing, orderNo={}", orderNo);
            return false;
        }
        Long userId = OrderRowMapper.longValue(row, "userId");
        publishClosingFinalize(orderNo, userId, closingDeadline);
        if (log.isInfoEnabled()) {
            log.info("[Order] persisted pending order entered closing grace, orderNo={}", orderNo);
        }
        return true;
    }

    private FinalizeClosingResult finalizeClosingInTransaction(String orderNo, OffsetDateTime now) {
        if (!Boolean.TRUE.equals(orderMapper.tryLockOrderState(orderNo))) {
            return FinalizeClosingResult.retry(null);
        }
        Map<String, Object> persisted = orderMapper.findOrderByOrderNo(orderNo);
        if (persisted != null && !persisted.isEmpty()) {
            return finalizePersistedOrder(orderNo, persisted, now);
        }
        OrderRedisStateChangeResult redisResult = orderRedisSnapshotService.finalizeClosing(orderNo, now);
        if (!redisResult.changed()) {
            return FinalizeClosingResult.unchanged();
        }
        Long userId = OrderRowMapper.longValue(redisResult.order(), "userId");
        return FinalizeClosingResult.changed(userId, redisResult.items(), hasUserCoupon(redisResult.order()));
    }

    private FinalizeClosingResult finalizePersistedOrder(String orderNo, Map<String, Object> persisted, OffsetDateTime now) {
        String status = OrderRowMapper.text(persisted, "status");
        Long userId = OrderRowMapper.longValue(persisted, "userId");
        if (OrderStatus.PAID.equals(status)
                || OrderStatus.CLOSED.equals(status)
                || OrderStatus.CANCELLED.equals(status)) {
            cleanupRedisTerminalSnapshot(orderNo, userId, status);
            return FinalizeClosingResult.unchanged();
        }
        if (OrderStatus.PENDING_PAYMENT.equals(status)) {
            OffsetDateTime closingDeadline = now.plus(Duration.ofMillis(closingFinalizeWindowMillis()));
            Map<String, Object> updated = orderMapper.startClosingExpiredOrder(orderNo, now, closingDeadline);
            if (updated == null || updated.isEmpty()) {
                return FinalizeClosingResult.unchanged();
            }
            return FinalizeClosingResult.retry(OrderRowMapper.longValue(updated, "userId"));
        }
        if (!OrderStatus.CLOSING.equals(status)) {
            return FinalizeClosingResult.unchanged();
        }
        Map<String, Object> updated = orderMapper.closeClosingOrder(orderNo, now);
        if (updated == null || updated.isEmpty()) {
            return FinalizeClosingResult.unchanged();
        }
        List<Map<String, Object>> itemRows = orderMapper.listOrderItems(orderNo);
        Long updatedUserId = OrderRowMapper.longValue(updated, "userId");
        cleanupRedisTerminalSnapshot(orderNo, updatedUserId, OrderStatus.CLOSED);
        return FinalizeClosingResult.changed(updatedUserId, itemRows, hasUserCoupon(updated));
    }

    private void releaseResourcesIfNeeded(FinalizeClosingResult result, String orderNo, OffsetDateTime now) {
        if (result == null || !result.changed()) {
            return;
        }
        releaseResources(result.userId(), orderNo, result.itemRows(), result.hasUserCoupon(), now);
    }

    private void releaseResources(Long userId,
                                  String orderNo,
                                  List<Map<String, Object>> itemRows,
                                  boolean hasUserCoupon,
                                  OffsetDateTime now) {
        routedTransactionExecutor.executeWithoutResult(DataSourceRoute.PRODUCT, () ->
                orderInventoryReleaseService.release(orderNo, itemRows)
        );
        if (!hasUserCoupon) {
            return;
        }
        routedTransactionExecutor.executeWithoutResult(DataSourceRoute.TRADE, () -> {
            LockedOrderCoupon releasedCoupon = orderCouponService.releaseLockedCoupon(orderNo, now);
            orderCouponUsageService.writeRelease(userId, releasedCoupon, orderNo);
        });
    }

    private void publishClosingFinalize(String orderNo, Long userId, OffsetDateTime closingDeadline) {
        long closingDeadlineEpochMilli = closingDeadline.toInstant().toEpochMilli();
        try {
            orderExpireMessagePublisher.publishClosingFinalizeCheck(
                    orderNo,
                    userId,
                    closingDeadlineEpochMilli
            );
        } catch (Exception e) {
            log.warn(
                    "[OrderRabbit] publish threw exception, phase=closing-finalize, orderNo={}, userId={}, exchange={}, routingKey={}, closingDeadlineEpochMilli={}",
                    orderNo,
                    userId,
                    orderExpireRabbitProperties.getClosingFinalizeExchange(),
                    orderExpireRabbitProperties.getClosingFinalizeRoutingKey(),
                    closingDeadlineEpochMilli,
                    e
            );
        }
    }

    private long closingFinalizeWindowMillis() {
        return Math.max(1L, orderExpireRabbitProperties.closingFinalizeWindowMillis());
    }

    private boolean hasUserCoupon(java.util.Map<String, Object> order) {
        return !OrderRowMapper.idText(order, "userCouponId").isBlank();
    }

    private void cleanupRedisTerminalSnapshot(String orderNo, Long userId, String status) {
        Map<String, Object> order = new java.util.LinkedHashMap<>();
        order.put("orderNo", orderNo);
        order.put("userId", userId);
        order.put("status", status);
        try {
            orderRedisSnapshotService.completePersistedAndCleanup(
                    List.of(orderNo),
                    List.of(new OrderRedisSnapshot(order, List.of()))
            );
        } catch (Exception e) {
            log.warn("[Order] terminal Redis snapshot cleanup failed, orderNo={}, status={}", orderNo, status, e);
        }
    }

    private record FinalizeClosingResult(boolean changed,
                                         boolean retry,
                                         Long userId,
                                         List<Map<String, Object>> itemRows,
                                         boolean hasUserCoupon) {

        private static FinalizeClosingResult changed(Long userId,
                                                     List<Map<String, Object>> itemRows,
                                                     boolean hasUserCoupon) {
            return new FinalizeClosingResult(true, false, userId, itemRows == null ? List.of() : itemRows, hasUserCoupon);
        }

        private static FinalizeClosingResult retry(Long userId) {
            return new FinalizeClosingResult(false, true, userId, List.of(), false);
        }

        private static FinalizeClosingResult unchanged() {
            return new FinalizeClosingResult(false, false, null, List.of(), false);
        }
    }
}
