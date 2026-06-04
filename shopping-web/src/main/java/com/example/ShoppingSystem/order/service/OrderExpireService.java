package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.rabbit.OrderExpireMessage;
import com.example.ShoppingSystem.order.rabbit.OrderExpireMessagePublisher;
import com.example.ShoppingSystem.order.rabbit.OrderExpireRabbitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class OrderExpireService {

    private static final Logger log = LoggerFactory.getLogger(OrderExpireService.class);

    private final OrderInventoryReleaseService orderInventoryReleaseService;
    private final OrderCouponService orderCouponService;
    private final OrderCouponUsageService orderCouponUsageService;
    private final OrderMapper orderMapper;
    private final TransactionTemplate transactionTemplate;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderExpireMessagePublisher orderExpireMessagePublisher;
    private final OrderExpireRabbitProperties orderExpireRabbitProperties;

    public OrderExpireService(OrderInventoryReleaseService orderInventoryReleaseService,
                              OrderCouponService orderCouponService,
                              OrderCouponUsageService orderCouponUsageService,
                              OrderMapper orderMapper,
                              TransactionTemplate transactionTemplate,
                              OrderRedisSnapshotService orderRedisSnapshotService,
                              OrderExpireMessagePublisher orderExpireMessagePublisher,
                              OrderExpireRabbitProperties orderExpireRabbitProperties) {
        this.orderInventoryReleaseService = orderInventoryReleaseService;
        this.orderCouponService = orderCouponService;
        this.orderCouponUsageService = orderCouponUsageService;
        this.orderMapper = orderMapper;
        this.transactionTemplate = transactionTemplate;
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
        OffsetDateTime closingDeadline = now.plus(Duration.ofMillis(closingGraceMillis()));
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
            return startClosingPersistedOrder(normalizedOrderNo, now, closingDeadline);
        }
        return false;
    }

    public boolean finalizeClosing(String orderNo) {
        String normalizedOrderNo = orderNo == null ? "" : orderNo.trim();
        if (normalizedOrderNo.isEmpty()) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        OrderRedisStateChangeResult redisResult = orderRedisSnapshotService.finalizeClosing(normalizedOrderNo, now);
        if (redisResult.changed()) {
            Long userId = OrderRowMapper.longValue(redisResult.order(), "userId");
            transactionTemplate.executeWithoutResult(status -> releaseResources(
                    userId,
                    normalizedOrderNo,
                    redisResult.items(),
                    hasUserCoupon(redisResult.order()),
                    now
            ));
            if (log.isInfoEnabled()) {
                log.info("[Order] finalized closing order, orderNo={}", normalizedOrderNo);
            }
            return true;
        }
        if ("ORDER_FINALIZE_CLOSING_1".equals(redisResult.code())) {
            return finalizePersistedClosingOrder(normalizedOrderNo, now);
        }
        return false;
    }

    private boolean startClosingPersistedOrder(String orderNo, OffsetDateTime now, OffsetDateTime closingDeadline) {
        Map<String, Object> row = orderMapper.startClosingExpiredOrder(orderNo, now, closingDeadline);
        if (row == null || row.isEmpty()) {
            return false;
        }
        publishClosingFinalize(orderNo, OrderRowMapper.longValue(row, "userId"), closingDeadline);
        if (log.isInfoEnabled()) {
            log.info("[Order] persisted pending order entered closing grace, orderNo={}", orderNo);
        }
        return true;
    }

    private boolean finalizePersistedClosingOrder(String orderNo, OffsetDateTime now) {
        Map<String, Object> row = transactionTemplate.execute(status -> {
            Map<String, Object> updated = orderMapper.closeClosingOrder(orderNo, now);
            if (updated == null || updated.isEmpty()) {
                return null;
            }
            List<Map<String, Object>> itemRows = orderMapper.listOrderItems(orderNo);
            Long userId = OrderRowMapper.longValue(updated, "userId");
            releaseResources(userId, orderNo, itemRows, hasUserCoupon(updated), now);
            return updated;
        });
        if (row == null || row.isEmpty()) {
            return false;
        }
        if (log.isInfoEnabled()) {
            log.info("[Order] finalized persisted closing order, orderNo={}", orderNo);
        }
        return true;
    }

    private void releaseResources(Long userId,
                                  String orderNo,
                                  List<Map<String, Object>> itemRows,
                                  boolean hasUserCoupon,
                                  OffsetDateTime now) {
        orderInventoryReleaseService.release(orderNo, itemRows);
        LockedOrderCoupon releasedCoupon = hasUserCoupon
                ? orderCouponService.releaseLockedCoupon(orderNo, now)
                : null;
        orderCouponUsageService.writeRelease(userId, releasedCoupon, orderNo);
    }

    private void publishClosingFinalize(String orderNo, Long userId, OffsetDateTime closingDeadline) {
        try {
            orderExpireMessagePublisher.publishClosingFinalize(OrderExpireMessage.closingFinalize(
                    orderNo,
                    userId,
                    closingDeadline.toInstant().toEpochMilli()
            ));
        } catch (Exception e) {
            log.warn("[Order] closing finalize message publish failed, orderNo={}", orderNo, e);
        }
    }

    private long closingGraceMillis() {
        return Math.max(1L, orderExpireRabbitProperties.getClosingGraceMillis());
    }

    private boolean hasUserCoupon(java.util.Map<String, Object> order) {
        return !OrderRowMapper.idText(order, "userCouponId").isBlank();
    }
}
