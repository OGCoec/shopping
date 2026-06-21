package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.outbox.OutboxEventRequest;
import com.example.ShoppingSystem.outbox.annotation.OutboxEventCollector;
import com.example.ShoppingSystem.outbox.annotation.TransactionalOutbox;
import com.example.ShoppingSystem.outbox.orderinventory.OrderInventoryReleaseRequestedMessage;
import com.example.ShoppingSystem.outbox.orderinventory.OrderInventoryReleaseRequestedRouting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OrderInventoryReleaseRequestWriter {

    public static final String REASON_CANCEL = "CANCEL";
    public static final String REASON_EXPIRE_CLOSE = "EXPIRE_CLOSE";
    public static final String REASON_CLOSING_COMPENSATE = "CLOSING_COMPENSATE";

    private static final Logger log = LoggerFactory.getLogger(OrderInventoryReleaseRequestWriter.class);

    private final OrderMapper orderMapper;
    private final OrderCouponService orderCouponService;
    private final OrderCouponUsageService orderCouponUsageService;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OutboxEventCollector outboxEventCollector;

    public OrderInventoryReleaseRequestWriter(OrderMapper orderMapper,
                                              OrderCouponService orderCouponService,
                                              OrderCouponUsageService orderCouponUsageService,
                                              OrderRedisSnapshotService orderRedisSnapshotService,
                                              OutboxEventCollector outboxEventCollector) {
        this.orderMapper = orderMapper;
        this.orderCouponService = orderCouponService;
        this.orderCouponUsageService = orderCouponUsageService;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.outboxEventCollector = outboxEventCollector;
    }

    @TransactionalOutbox(DataSourceRoute.TRADE)
    public void requestRelease(Long userId,
                               String orderNo,
                               List<Map<String, Object>> itemRows,
                               boolean hasUserCoupon,
                               OffsetDateTime now,
                               String reason) {
        releaseCouponIfNeeded(userId, orderNo, hasUserCoupon, now);
        registerReleaseEvent(userId, orderNo, itemRows, now, reason);
    }

    @TransactionalOutbox(DataSourceRoute.TRADE)
    public CancelPersistedResult cancelPendingAndRequestRelease(Long userId, String orderNo, OffsetDateTime now) {
        Map<String, Object> updated = orderMapper.cancelPendingOrder(orderNo, userId, now);
        if (updated == null || updated.isEmpty()) {
            throw new OrderServiceException("ORDER_CANCEL_UNAVAILABLE", "Only pending current-user orders can be cancelled.", HttpStatus.CONFLICT);
        }
        List<Map<String, Object>> itemRows = orderMapper.listOrderItems(orderNo);
        releaseCouponIfNeeded(userId, orderNo, hasUserCoupon(updated), now);
        registerReleaseEvent(userId, orderNo, itemRows, now, REASON_CANCEL);
        return new CancelPersistedResult(updated, itemRows);
    }

    @TransactionalOutbox(DataSourceRoute.TRADE)
    public FinalizeClosingResult finalizeClosingAndRequestRelease(String orderNo,
                                                                  OffsetDateTime now,
                                                                  OffsetDateTime closingDeadline) {
        if (!Boolean.TRUE.equals(orderMapper.tryLockOrderState(orderNo))) {
            return FinalizeClosingResult.retry(null);
        }
        Map<String, Object> persisted = orderMapper.findOrderByOrderNo(orderNo);
        if (persisted != null && !persisted.isEmpty()) {
            return finalizePersistedOrder(orderNo, persisted, now, closingDeadline);
        }
        OrderRedisStateChangeResult redisResult = orderRedisSnapshotService.finalizeClosing(orderNo, now);
        if (!redisResult.changed()) {
            return FinalizeClosingResult.unchanged();
        }
        Long userId = OrderRowMapper.longValue(redisResult.order(), "userId");
        requestReleaseInCurrentTransaction(userId, orderNo, redisResult.items(), hasUserCoupon(redisResult.order()), now, REASON_EXPIRE_CLOSE);
        return FinalizeClosingResult.changed(userId);
    }

    @TransactionalOutbox(DataSourceRoute.TRADE)
    public ResourceReleaseResult requestReleaseForSnapshots(List<OrderRedisSnapshot> snapshots,
                                                            OffsetDateTime now,
                                                            String reason) {
        if (snapshots == null || snapshots.isEmpty()) {
            return new ResourceReleaseResult(0, 0);
        }
        List<Map<String, Object>> released = orderCouponService.releaseLockedCoupons(snapshots);
        orderCouponUsageService.writeReleases(released);
        List<OutboxEventRequest> events = snapshots.stream()
                .map(snapshot -> releaseEventRequest(snapshot, now, reason))
                .filter(request -> request != null)
                .toList();
        outboxEventCollector.registerAll(events);
        return new ResourceReleaseResult(inventoryItemCount(snapshots), released.size());
    }

    private FinalizeClosingResult finalizePersistedOrder(String orderNo,
                                                         Map<String, Object> persisted,
                                                         OffsetDateTime now,
                                                         OffsetDateTime closingDeadline) {
        String status = OrderRowMapper.text(persisted, "status");
        Long userId = OrderRowMapper.longValue(persisted, "userId");
        if (OrderStatus.PAID.equals(status)
                || OrderStatus.CLOSED.equals(status)
                || OrderStatus.CANCELLED.equals(status)) {
            cleanupRedisTerminalSnapshot(orderNo, userId, status);
            return FinalizeClosingResult.unchanged();
        }
        if (OrderStatus.PENDING_PAYMENT.equals(status)) {
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
        requestReleaseInCurrentTransaction(updatedUserId, orderNo, itemRows, hasUserCoupon(updated), now, REASON_EXPIRE_CLOSE);
        return FinalizeClosingResult.changed(updatedUserId);
    }

    private void requestReleaseInCurrentTransaction(Long userId,
                                                    String orderNo,
                                                    List<Map<String, Object>> itemRows,
                                                    boolean hasUserCoupon,
                                                    OffsetDateTime now,
                                                    String reason) {
        releaseCouponIfNeeded(userId, orderNo, hasUserCoupon, now);
        registerReleaseEvent(userId, orderNo, itemRows, now, reason);
    }

    private int releaseCouponIfNeeded(Long userId, String orderNo, boolean hasUserCoupon, OffsetDateTime now) {
        if (!hasUserCoupon) {
            return 0;
        }
        LockedOrderCoupon releasedCoupon = orderCouponService.releaseLockedCoupon(orderNo, now);
        orderCouponUsageService.writeRelease(userId, releasedCoupon, orderNo);
        return releasedCoupon == null ? 0 : 1;
    }

    private void registerReleaseEvent(Long userId,
                                      String orderNo,
                                      List<Map<String, Object>> itemRows,
                                      OffsetDateTime now,
                                      String reason) {
        OutboxEventRequest request = releaseEventRequest(orderNo, userId, itemRows, now, reason);
        if (request != null) {
            outboxEventCollector.register(request);
        }
    }

    private OutboxEventRequest releaseEventRequest(OrderRedisSnapshot snapshot, OffsetDateTime now, String reason) {
        if (snapshot == null || snapshot.items() == null || snapshot.items().isEmpty()) {
            return null;
        }
        Map<String, Object> order = snapshot.order();
        String orderNo = OrderRowMapper.text(order, "orderNo");
        Long userId = OrderRowMapper.longValue(order, "userId");
        return releaseEventRequest(orderNo, userId, snapshot.items(), now, reason);
    }

    private OutboxEventRequest releaseEventRequest(String orderNo,
                                                   Long userId,
                                                   List<Map<String, Object>> itemRows,
                                                   OffsetDateTime now,
                                                   String reason) {
        String normalizedOrderNo = orderNo == null ? "" : orderNo.trim();
        if (normalizedOrderNo.isEmpty() || itemRows == null || itemRows.isEmpty()) {
            return null;
        }
        String normalizedReason = normalizeReason(reason);
        String eventId = releaseEventId(normalizedOrderNo, normalizedReason);
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderNo", normalizedOrderNo);
        order.put("userId", userId);
        order.put("releaseReason", normalizedReason);
        OrderInventoryReleaseRequestedMessage message = new OrderInventoryReleaseRequestedMessage(
                eventId,
                normalizedOrderNo,
                userId,
                normalizedReason,
                order,
                itemRows,
                occurredAtEpochMillis(now)
        );
        return new OutboxEventRequest(
                eventId,
                OrderInventoryReleaseRequestedRouting.EVENT_TYPE,
                OrderInventoryReleaseRequestedRouting.AGGREGATE_TYPE,
                normalizedOrderNo,
                OrderInventoryReleaseRequestedRouting.EXCHANGE,
                OrderInventoryReleaseRequestedRouting.ROUTING_KEY,
                message,
                eventId
        );
    }

    private String releaseEventId(String orderNo, String reason) {
        return "order-inventory-release:" + orderNo + ":" + reason;
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        return normalized.isEmpty() ? "UNKNOWN" : normalized;
    }

    private long occurredAtEpochMillis(OffsetDateTime now) {
        return (now == null ? OffsetDateTime.now() : now).toInstant().toEpochMilli();
    }

    private int inventoryItemCount(List<OrderRedisSnapshot> snapshots) {
        return (int) snapshots.stream()
                .filter(snapshot -> snapshot != null && snapshot.items() != null)
                .flatMap(snapshot -> snapshot.items().stream())
                .filter(row -> OrderRowMapper.intValue(row, "quantity", 0) > 0)
                .count();
    }

    private boolean hasUserCoupon(Map<String, Object> order) {
        return !OrderRowMapper.idText(order, "userCouponId").isBlank()
                || !OrderRowMapper.text(order, "userCouponIdHex").isBlank();
    }

    private void cleanupRedisTerminalSnapshot(String orderNo, Long userId, String status) {
        Map<String, Object> order = new LinkedHashMap<>();
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

    public record CancelPersistedResult(Map<String, Object> order,
                                        List<Map<String, Object>> itemRows) {
    }

    public record FinalizeClosingResult(boolean changed,
                                        boolean retry,
                                        Long userId) {

        private static FinalizeClosingResult changed(Long userId) {
            return new FinalizeClosingResult(true, false, userId);
        }

        private static FinalizeClosingResult retry(Long userId) {
            return new FinalizeClosingResult(false, true, userId);
        }

        private static FinalizeClosingResult unchanged() {
            return new FinalizeClosingResult(false, false, null);
        }
    }

    public record ResourceReleaseResult(int inventoryItemCount,
                                        int couponCount) {
    }
}
