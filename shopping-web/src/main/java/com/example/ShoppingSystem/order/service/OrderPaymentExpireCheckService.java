package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.rabbit.OrderExpireMessagePublisher;
import com.example.ShoppingSystem.order.rabbit.OrderPaymentExpireCheckMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class OrderPaymentExpireCheckService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentExpireCheckService.class);

    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderMapper orderMapper;
    private final PaymentCallbackPendingMarkerService paymentCallbackPendingMarkerService;
    private final PaymentStatusQueryService paymentStatusQueryService;
    private final OrderPaymentSuccessService orderPaymentSuccessService;
    private final OrderExpireService orderExpireService;
    private final OrderExpireMessagePublisher orderExpireMessagePublisher;

    public OrderPaymentExpireCheckService(OrderRedisSnapshotService orderRedisSnapshotService,
                                          OrderMapper orderMapper,
                                          PaymentCallbackPendingMarkerService paymentCallbackPendingMarkerService,
                                          PaymentStatusQueryService paymentStatusQueryService,
                                          OrderPaymentSuccessService orderPaymentSuccessService,
                                          OrderExpireService orderExpireService,
                                          OrderExpireMessagePublisher orderExpireMessagePublisher) {
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.orderMapper = orderMapper;
        this.paymentCallbackPendingMarkerService = paymentCallbackPendingMarkerService;
        this.paymentStatusQueryService = paymentStatusQueryService;
        this.orderPaymentSuccessService = orderPaymentSuccessService;
        this.orderExpireService = orderExpireService;
        this.orderExpireMessagePublisher = orderExpireMessagePublisher;
    }

    public void check(OrderPaymentExpireCheckMessage message) {
        String orderNo = normalize(message == null ? null : message.orderNo());
        if (orderNo.isEmpty()) {
            log.warn("[Order] invalid payment expire check message skipped, message={}", message);
            return;
        }
        String status = currentOrderStatus(orderNo);
        if (!OrderStatus.PENDING_PAYMENT.equals(status)) {
            if (log.isDebugEnabled()) {
                log.debug("[Order] payment expire check skipped, orderNo={}, status={}", orderNo, status);
            }
            return;
        }
        if (paymentCallbackPendingMarkerService.hasReceived(orderNo)) {
            continueCheckOrStartClosing(message, orderNo, "callback-received");
            return;
        }
        PaymentStatusQueryResult queryResult = queryPaymentStatus(orderNo, message.userId());
        if (PaymentStatusQueryStatus.PAID.equals(queryResult.status())) {
            markPaidFromPaymentQuery(orderNo, queryResult);
            return;
        }
        continueCheckOrStartClosing(message, orderNo, "payment-status-" + queryResult.status());
    }

    private String currentOrderStatus(String orderNo) {
        OrderRedisSnapshot snapshot = orderRedisSnapshotService.findSnapshot(orderNo).orElse(null);
        if (snapshot != null) {
            return normalizedStatus(OrderRowMapper.text(snapshot.order(), "status"));
        }
        Map<String, Object> row = orderMapper.findOrderByOrderNo(orderNo);
        if (row == null || row.isEmpty()) {
            return "NOT_FOUND";
        }
        return normalizedStatus(OrderRowMapper.text(row, "status"));
    }

    private PaymentStatusQueryResult queryPaymentStatus(String orderNo, Long userId) {
        try {
            PaymentStatusQueryResult result = paymentStatusQueryService.query(orderNo, userId);
            return result == null ? PaymentStatusQueryResult.unknown() : result;
        } catch (Exception e) {
            log.warn("[Order] payment status query failed, orderNo={}", orderNo, e);
            return PaymentStatusQueryResult.unknown();
        }
    }

    private void markPaidFromPaymentQuery(String orderNo, PaymentStatusQueryResult queryResult) {
        OffsetDateTime paidAt = queryResult.paidAt() == null ? OffsetDateTime.now() : queryResult.paidAt();
        OrderPaymentMarkResult markResult = orderPaymentSuccessService.markPaid(
                orderNo,
                paidAt,
                queryResult.externalTradeNo(),
                queryResult.paidAmountYuan(),
                queryResult.paymentProvider()
        );
        if (markResult.refundPending()) {
            log.warn("[Order] payment query detected paid order but result requires refund, orderNo={}, status={}, refundNo={}",
                    orderNo, markResult.orderStatus(), markResult.refundNo());
        }
    }

    private void continueCheckOrStartClosing(OrderPaymentExpireCheckMessage message, String orderNo, String reason) {
        if (orderExpireMessagePublisher.publishNextPaymentCheck(message)) {
            if (log.isDebugEnabled()) {
                log.debug("[Order] payment expire check requeued, orderNo={}, reason={}", orderNo, reason);
            }
            return;
        }
        orderExpireService.startClosing(orderNo);
    }

    private String normalizedStatus(String status) {
        String value = normalize(status);
        return value.isEmpty() ? "UNKNOWN" : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
