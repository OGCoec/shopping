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
import com.example.ShoppingSystem.order.service.OrderInventoryReleaseRequestWriter;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshot;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderRedisStateChangeResult;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderStatus;
@Service
public class OrderExpireServiceImpl implements OrderExpireService {

    private static final Logger log = LoggerFactory.getLogger(OrderExpireService.class);

    private final OrderInventoryReleaseRequestWriter orderInventoryReleaseRequestWriter;
    private final OrderMapper orderMapper;
    private final RoutedTransactionExecutor routedTransactionExecutor;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderExpireMessagePublisher orderExpireMessagePublisher;
    private final OrderExpireRabbitProperties orderExpireRabbitProperties;

    public OrderExpireServiceImpl(OrderInventoryReleaseRequestWriter orderInventoryReleaseRequestWriter,
                                  OrderMapper orderMapper,
                                  RoutedTransactionExecutor routedTransactionExecutor,
                                  OrderRedisSnapshotService orderRedisSnapshotService,
                                  OrderExpireMessagePublisher orderExpireMessagePublisher,
                                  OrderExpireRabbitProperties orderExpireRabbitProperties) {
        this.orderInventoryReleaseRequestWriter = orderInventoryReleaseRequestWriter;
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
        OrderInventoryReleaseRequestWriter.FinalizeClosingResult result =
                orderInventoryReleaseRequestWriter.finalizeClosingAndRequestRelease(
                        normalizedOrderNo,
                        now,
                        now.plus(Duration.ofMillis(closingFinalizeWindowMillis()))
        );
        if (result == null) {
            return false;
        }
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

}
