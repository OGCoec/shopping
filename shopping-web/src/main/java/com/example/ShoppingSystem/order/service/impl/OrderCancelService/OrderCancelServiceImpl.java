package com.example.ShoppingSystem.order.service.impl.OrderCancelService;

import com.example.ShoppingSystem.order.dto.OrderCancelResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.example.ShoppingSystem.order.service.OrderCancelService;
import com.example.ShoppingSystem.order.service.OrderInventoryReleaseRequestWriter;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderRedisStateChangeResult;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
@Service
public class OrderCancelServiceImpl implements OrderCancelService {

    private final OrderInventoryReleaseRequestWriter orderInventoryReleaseRequestWriter;
    private final OrderRedisSnapshotService orderRedisSnapshotService;

    public OrderCancelServiceImpl(OrderInventoryReleaseRequestWriter orderInventoryReleaseRequestWriter,
                                  OrderRedisSnapshotService orderRedisSnapshotService) {
        this.orderInventoryReleaseRequestWriter = orderInventoryReleaseRequestWriter;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
    }

    public OrderCancelResponse cancel(Long userId, String orderNo) {
        String normalizedOrderNo = normalizeOrderNo(orderNo);
        OffsetDateTime now = OffsetDateTime.now();
        OrderRedisStateChangeResult redisResult = orderRedisSnapshotService.cancelPending(userId, normalizedOrderNo, now);
        if (redisResult.changed()) {
            orderInventoryReleaseRequestWriter.requestRelease(
                    userId,
                    normalizedOrderNo,
                    redisResult.items(),
                    hasUserCoupon(redisResult.order()),
                    now,
                    OrderInventoryReleaseRequestWriter.REASON_CANCEL
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
        OrderInventoryReleaseRequestWriter.CancelPersistedResult result =
                orderInventoryReleaseRequestWriter.cancelPendingAndRequestRelease(userId, normalizedOrderNo, now);
        return new OrderCancelResponse(
                OrderRowMapper.text(result.order(), "orderNo"),
                OrderRowMapper.text(result.order(), "status"),
                now
        );
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
