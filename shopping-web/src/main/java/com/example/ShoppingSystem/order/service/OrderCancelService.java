package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.dto.OrderCancelResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class OrderCancelService {

    private final OrderMapper orderMapper;
    private final OrderInventoryReleaseService orderInventoryReleaseService;
    private final OrderCouponService orderCouponService;
    private final OrderCouponUsageService orderCouponUsageService;
    private final TransactionTemplate transactionTemplate;
    private final OrderRedisSnapshotService orderRedisSnapshotService;

    public OrderCancelService(OrderMapper orderMapper,
                              OrderInventoryReleaseService orderInventoryReleaseService,
                              OrderCouponService orderCouponService,
                              OrderCouponUsageService orderCouponUsageService,
                              TransactionTemplate transactionTemplate,
                              OrderRedisSnapshotService orderRedisSnapshotService) {
        this.orderMapper = orderMapper;
        this.orderInventoryReleaseService = orderInventoryReleaseService;
        this.orderCouponService = orderCouponService;
        this.orderCouponUsageService = orderCouponUsageService;
        this.transactionTemplate = transactionTemplate;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
    }

    public OrderCancelResponse cancel(Long userId, String orderNo) {
        String normalizedOrderNo = normalizeOrderNo(orderNo);
        OffsetDateTime now = OffsetDateTime.now();
        OrderRedisStateChangeResult redisResult = orderRedisSnapshotService.cancelPending(userId, normalizedOrderNo, now);
        if (redisResult.changed()) {
            releaseResources(
                    userId,
                    normalizedOrderNo,
                    redisResult.items(),
                    hasUserCoupon(redisResult.order()),
                    now
            );
            return new OrderCancelResponse(
                    OrderRowMapper.text(redisResult.order(), "orderNo"),
                    OrderRowMapper.text(redisResult.order(), "status"),
                    OrderRowMapper.offsetDateTime(redisResult.order(), "cancelledAt")
            );
        }
        if (!"ORDER_CANCEL_1".equals(redisResult.code())) {
            throw new OrderServiceException("ORDER_CANCEL_UNAVAILABLE", "Only pending current-user orders can be cancelled.", HttpStatus.CONFLICT);
        }
        Map<String, Object> row = transactionTemplate.execute(status -> {
            Map<String, Object> updated = orderMapper.cancelPendingOrder(normalizedOrderNo, userId, now);
            if (updated == null || updated.isEmpty()) {
                throw new OrderServiceException("ORDER_CANCEL_UNAVAILABLE", "Only pending current-user orders can be cancelled.", HttpStatus.CONFLICT);
            }
            List<Map<String, Object>> itemRows = orderMapper.listOrderItems(normalizedOrderNo);
            releaseResources(userId, normalizedOrderNo, itemRows, hasUserCoupon(updated), now);
            return updated;
        });
        return new OrderCancelResponse(
                OrderRowMapper.text(row, "orderNo"),
                OrderRowMapper.text(row, "status"),
                now
        );
    }

    private void releaseResources(Long userId,
                                  String orderNo,
                                  List<Map<String, Object>> itemRows,
                                  boolean hasUserCoupon,
                                  OffsetDateTime now) {
        transactionTemplate.executeWithoutResult(status -> {
            orderInventoryReleaseService.release(orderNo, itemRows);
            LockedOrderCoupon releasedCoupon = hasUserCoupon
                    ? orderCouponService.releaseLockedCoupon(orderNo, now)
                    : null;
            orderCouponUsageService.writeRelease(userId, releasedCoupon, orderNo);
        });
    }

    private boolean hasUserCoupon(Map<String, Object> order) {
        return !OrderRowMapper.idText(order, "userCouponId").isBlank();
    }

    private String normalizeOrderNo(String orderNo) {
        String value = orderNo == null ? "" : orderNo.trim();
        if (value.isEmpty() || value.length() > 64) {
            throw new OrderServiceException("ORDER_NO_INVALID", "Order number is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }
}
