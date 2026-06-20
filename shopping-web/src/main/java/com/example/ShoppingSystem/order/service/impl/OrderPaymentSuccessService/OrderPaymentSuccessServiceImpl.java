package com.example.ShoppingSystem.order.service.impl.OrderPaymentSuccessService;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.dto.PaymentRefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import com.example.ShoppingSystem.order.service.OrderPaymentSuccessService;
import com.example.ShoppingSystem.order.service.LockedOrderCoupon;
import com.example.ShoppingSystem.order.service.OrderAmountCalculator;
import com.example.ShoppingSystem.order.service.OrderCardSecretDeliveryService;
import com.example.ShoppingSystem.order.service.OrderCouponService;
import com.example.ShoppingSystem.order.service.OrderCouponUsageService;
import com.example.ShoppingSystem.order.service.OrderPaymentMarkResult;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshot;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderRedisStateChangeResult;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderStatus;
import com.example.ShoppingSystem.order.service.PaymentRefundReasonCode;
import com.example.ShoppingSystem.order.service.PaymentRefundService;
@Service
public class OrderPaymentSuccessServiceImpl implements OrderPaymentSuccessService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentSuccessService.class);

    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderMapper orderMapper;
    private final OrderCouponService orderCouponService;
    private final OrderCouponUsageService orderCouponUsageService;
    private final PaymentRefundService paymentRefundService;
    private final OrderCardSecretDeliveryService orderCardSecretDeliveryService;
    private final RoutedTransactionExecutor routedTransactionExecutor;

    public OrderPaymentSuccessServiceImpl(OrderRedisSnapshotService orderRedisSnapshotService,
                                      OrderMapper orderMapper,
                                      OrderCouponService orderCouponService,
                                      OrderCouponUsageService orderCouponUsageService,
                                      PaymentRefundService paymentRefundService,
                                      OrderCardSecretDeliveryService orderCardSecretDeliveryService,
                                      RoutedTransactionExecutor routedTransactionExecutor) {
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.orderMapper = orderMapper;
        this.orderCouponService = orderCouponService;
        this.orderCouponUsageService = orderCouponUsageService;
        this.paymentRefundService = paymentRefundService;
        this.orderCardSecretDeliveryService = orderCardSecretDeliveryService;
        this.routedTransactionExecutor = routedTransactionExecutor;
    }

    public OrderPaymentMarkResult markPaid(String orderNo,
                                           OffsetDateTime paidAt,
                                           String externalTradeNo,
                                           BigDecimal paidAmountYuan,
                                           String paymentProvider) {
        String normalizedOrderNo = orderNo == null ? "" : orderNo.trim();
        if (normalizedOrderNo.isEmpty()) {
            return refundForAbnormalOrder(
                    normalizedOrderNo,
                    "NOT_FOUND",
                    Map.of(),
                    paidAt == null ? OffsetDateTime.now() : paidAt,
                    externalTradeNo,
                    paidAmountYuan,
                    paymentProvider
            );
        }
        OffsetDateTime actualPaidAt = paidAt == null ? OffsetDateTime.now() : paidAt;
        OrderRedisStateChangeResult redisResult = orderRedisSnapshotService.markPaid(
                normalizedOrderNo,
                actualPaidAt,
                externalTradeNo
        );
        if (redisResult.changed()) {
            boolean idempotent = "ORDER_PAY_4".equals(redisResult.code());
            if (!idempotent) {
                routedTransactionExecutor.executeWithoutResult(DataSourceRoute.TRADE, () -> useCouponIfNeeded(
                        redisResult.order(),
                        normalizedOrderNo,
                        actualPaidAt
                ));
                log.info("[Order] order marked paid from Redis snapshot, orderNo={}, externalTradeNo={}",
                        normalizedOrderNo, safeExternalTradeNo(externalTradeNo));
            }
            deliverCardSecrets(normalizedOrderNo, OrderRowMapper.longValue(redisResult.order(), "userId"), redisResult.items());
            return OrderPaymentMarkResult.paid(normalizedOrderNo, actualPaidAt, externalTradeNo, idempotent);
        }
        if ("ORDER_PAY_1".equals(redisResult.code())) {
            OrderPaymentMarkResult result = markPersistedOrderPaid(
                    normalizedOrderNo,
                    actualPaidAt,
                    externalTradeNo
            );
            if (result != null) {
                return result;
            }
        }
        log.warn("[Order] payment success ignored because order status is not payable, orderNo={}, code={}, externalTradeNo={}",
                normalizedOrderNo, redisResult.code(), safeExternalTradeNo(externalTradeNo));
        Map<String, Object> abnormalOrder = currentOrder(normalizedOrderNo);
        String currentStatus = orderStatus(abnormalOrder);
        return refundForAbnormalOrder(
                normalizedOrderNo,
                currentStatus,
                abnormalOrder,
                actualPaidAt,
                externalTradeNo,
                paidAmountYuan,
                paymentProvider
        );
    }

    public boolean markPendingPaidForUser(Long userId, String orderNo, OffsetDateTime paidAt, String externalTradeNo) {
        String normalizedOrderNo = orderNo == null ? "" : orderNo.trim();
        if (userId == null || normalizedOrderNo.isEmpty()) {
            return false;
        }
        OffsetDateTime actualPaidAt = paidAt == null ? OffsetDateTime.now() : paidAt;
        OrderRedisStateChangeResult redisResult = orderRedisSnapshotService.markPendingPaidForUser(
                normalizedOrderNo,
                userId,
                actualPaidAt,
                externalTradeNo
        );
        if (redisResult.changed()) {
            routedTransactionExecutor.executeWithoutResult(DataSourceRoute.TRADE, () -> useCouponIfNeeded(
                    redisResult.order(),
                    normalizedOrderNo,
                    actualPaidAt
            ));
            log.info("[Order] current-user order marked paid from Redis snapshot, userId={}, orderNo={}, externalTradeNo={}",
                    userId, normalizedOrderNo, safeExternalTradeNo(externalTradeNo));
            deliverCardSecrets(normalizedOrderNo, userId, redisResult.items());
            return true;
        }
        if ("ORDER_PAY_1".equals(redisResult.code())) {
            return markPersistedPendingOrderPaidForUser(userId, normalizedOrderNo, actualPaidAt, externalTradeNo);
        }
        log.warn("[Order] current-user payment ignored because order is not pending, userId={}, orderNo={}, code={}, externalTradeNo={}",
                userId, normalizedOrderNo, redisResult.code(), safeExternalTradeNo(externalTradeNo));
        return false;
    }

    private OrderPaymentMarkResult markPersistedOrderPaid(String orderNo, OffsetDateTime paidAt, String externalTradeNo) {
        Map<String, Object> row = routedTransactionExecutor.execute(DataSourceRoute.TRADE, () -> {
            Map<String, Object> updated = orderMapper.markPaidOrder(orderNo, paidAt);
            if (updated != null && OrderRowMapper.boolValue(updated, "changed")) {
                useCouponIfNeeded(updated, orderNo, paidAt);
            }
            return updated;
        });
        if (row == null || row.isEmpty()) {
            log.warn("[Order] payment success ignored because persisted order is not payable, orderNo={}, externalTradeNo={}",
                    orderNo, safeExternalTradeNo(externalTradeNo));
            return null;
        }
        boolean changed = OrderRowMapper.boolValue(row, "changed");
        if (changed) {
            log.info("[Order] persisted order marked paid, orderNo={}, externalTradeNo={}",
                    orderNo, safeExternalTradeNo(externalTradeNo));
        }
        deliverCardSecrets(orderNo, OrderRowMapper.longValue(row, "userId"), null);
        return OrderPaymentMarkResult.paid(orderNo, paidAt, externalTradeNo, !changed);
    }

    private boolean markPersistedPendingOrderPaidForUser(Long userId,
                                                         String orderNo,
                                                         OffsetDateTime paidAt,
                                                         String externalTradeNo) {
        Map<String, Object> row = routedTransactionExecutor.execute(DataSourceRoute.TRADE, () -> {
            Map<String, Object> updated = orderMapper.markPendingPaidOrderForUser(orderNo, userId, paidAt);
            if (updated != null && OrderRowMapper.boolValue(updated, "changed")) {
                useCouponIfNeeded(updated, orderNo, paidAt);
            }
            return updated;
        });
        if (row == null || row.isEmpty()) {
            log.warn("[Order] current-user payment ignored because persisted order is not pending, userId={}, orderNo={}, externalTradeNo={}",
                    userId, orderNo, safeExternalTradeNo(externalTradeNo));
            return false;
        }
        if (OrderRowMapper.boolValue(row, "changed")) {
            log.info("[Order] persisted current-user order marked paid, userId={}, orderNo={}, externalTradeNo={}",
                    userId, orderNo, safeExternalTradeNo(externalTradeNo));
        }
        deliverCardSecrets(orderNo, userId, null);
        return true;
    }

    private void deliverCardSecrets(String orderNo, Long userId, java.util.List<Map<String, Object>> items) {
        orderCardSecretDeliveryService.deliverPaidOrder(orderNo, userId, items);
    }

    private void useCouponIfNeeded(Map<String, Object> order, String orderNo, OffsetDateTime paidAt) {
        if (!hasUserCoupon(order)) {
            return;
        }
        LockedOrderCoupon usedCoupon = orderCouponService.useLockedCoupon(orderNo, paidAt);
        orderCouponUsageService.writeUse(
                OrderRowMapper.longValue(order, "userId"),
                usedCoupon,
                OrderAmountCalculator.money(OrderRowMapper.decimal(order, "totalAmountYuan")),
                OrderAmountCalculator.money(OrderRowMapper.decimal(order, "discountAmountYuan")),
                orderNo
        );
    }

    private boolean hasUserCoupon(Map<String, Object> order) {
        return !OrderRowMapper.idText(order, "userCouponId").isBlank();
    }

    private Map<String, Object> currentOrder(String orderNo) {
        OrderRedisSnapshot snapshot = orderRedisSnapshotService.findSnapshot(orderNo).orElse(null);
        if (snapshot != null) {
            return snapshot.order();
        }
        Map<String, Object> row = orderMapper.findOrderByOrderNo(orderNo);
        return row == null ? Map.of() : row;
    }

    private OrderPaymentMarkResult refundForAbnormalOrder(String orderNo,
                                                          String orderStatus,
                                                          Map<String, Object> order,
                                                          OffsetDateTime paidAt,
                                                          String externalTradeNo,
                                                          BigDecimal paidAmountYuan,
                                                          String paymentProvider) {
        String reasonCode = refundReason(orderStatus);
        PaymentRefundResponse refund = paymentRefundService.createFromPaymentAbnormal(
                orderNo,
                externalTradeNo,
                paidAt,
                paidAmountYuan,
                paymentProvider,
                order == null || order.isEmpty() ? null : order,
                reasonCode
        );
        log.warn("[Order] abnormal payment converted to refund pending, orderNo={}, orderStatus={}, refundNo={}, reasonCode={}",
                orderNo, orderStatus, refund.refundNo(), refund.reasonCode());
        return OrderPaymentMarkResult.refundPending(
                orderNo,
                orderStatus,
                paidAt,
                externalTradeNo,
                refund.refundNo(),
                refund.status(),
                refund.reasonCode()
        );
    }

    private String refundReason(String orderStatus) {
        if (OrderStatus.CLOSED.equals(orderStatus)) {
            return PaymentRefundReasonCode.PAID_AFTER_ORDER_CLOSED;
        }
        if (OrderStatus.CANCELLED.equals(orderStatus)) {
            return PaymentRefundReasonCode.PAID_AFTER_ORDER_CANCELLED;
        }
        if ("NOT_FOUND".equals(orderStatus)) {
            return PaymentRefundReasonCode.ORDER_NOT_FOUND_AFTER_PAID;
        }
        return PaymentRefundReasonCode.OTHER;
    }

    private String orderStatus(Map<String, Object> order) {
        if (order == null || order.isEmpty()) {
            return "NOT_FOUND";
        }
        String status = OrderRowMapper.text(order, "status");
        return status.isBlank() ? "UNKNOWN" : status;
    }

    private String safeExternalTradeNo(String externalTradeNo) {
        return externalTradeNo == null ? "" : externalTradeNo.trim();
    }
}
