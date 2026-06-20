package com.example.ShoppingSystem.order.service.impl.OrderCancelService;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.dto.OrderCancelResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.example.ShoppingSystem.order.service.OrderCancelService;
import com.example.ShoppingSystem.order.service.LockedOrderCoupon;
import com.example.ShoppingSystem.order.service.OrderCouponService;
import com.example.ShoppingSystem.order.service.OrderCouponUsageService;
import com.example.ShoppingSystem.order.service.OrderInventoryReleaseService;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderRedisStateChangeResult;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
@Service
public class OrderCancelServiceImpl implements OrderCancelService {

    private final OrderMapper orderMapper;
    private final OrderInventoryReleaseService orderInventoryReleaseService;
    private final OrderCouponService orderCouponService;
    private final OrderCouponUsageService orderCouponUsageService;
    private final RoutedTransactionExecutor routedTransactionExecutor;
    private final OrderRedisSnapshotService orderRedisSnapshotService;

    public OrderCancelServiceImpl(OrderMapper orderMapper,
                              OrderInventoryReleaseService orderInventoryReleaseService,
                              OrderCouponService orderCouponService,
                              OrderCouponUsageService orderCouponUsageService,
                              RoutedTransactionExecutor routedTransactionExecutor,
                              OrderRedisSnapshotService orderRedisSnapshotService) {
        this.orderMapper = orderMapper;
        this.orderInventoryReleaseService = orderInventoryReleaseService;
        this.orderCouponService = orderCouponService;
        this.orderCouponUsageService = orderCouponUsageService;
        this.routedTransactionExecutor = routedTransactionExecutor;
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
        CancelPersistedResult result = routedTransactionExecutor.execute(DataSourceRoute.TRADE, () -> {
            Map<String, Object> updated = orderMapper.cancelPendingOrder(normalizedOrderNo, userId, now);
            if (updated == null || updated.isEmpty()) {
                throw new OrderServiceException("ORDER_CANCEL_UNAVAILABLE", "Only pending current-user orders can be cancelled.", HttpStatus.CONFLICT);
            }
            List<Map<String, Object>> itemRows = orderMapper.listOrderItems(normalizedOrderNo);
            return new CancelPersistedResult(updated, itemRows);
        });
        releaseResources(userId, normalizedOrderNo, result.itemRows(), hasUserCoupon(result.order()), now);
        return new OrderCancelResponse(
                OrderRowMapper.text(result.order(), "orderNo"),
                OrderRowMapper.text(result.order(), "status"),
                now
        );
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

    private record CancelPersistedResult(Map<String, Object> order,
                                         List<Map<String, Object>> itemRows) {
    }
}
