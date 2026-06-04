package com.example.ShoppingSystem.order.service;

import java.time.OffsetDateTime;

public record OrderPaymentMarkResult(String outcome,
                                     String orderNo,
                                     String orderStatus,
                                     OffsetDateTime paidAt,
                                     String externalTradeNo,
                                     String refundNo,
                                     String refundStatus,
                                     String reasonCode) {

    public static final String OUTCOME_PAID = "PAID";
    public static final String OUTCOME_PAID_IDEMPOTENT = "PAID_IDEMPOTENT";
    public static final String OUTCOME_REFUND_PENDING = "REFUND_PENDING";

    public static OrderPaymentMarkResult paid(String orderNo,
                                              OffsetDateTime paidAt,
                                              String externalTradeNo,
                                              boolean idempotent) {
        return new OrderPaymentMarkResult(
                idempotent ? OUTCOME_PAID_IDEMPOTENT : OUTCOME_PAID,
                orderNo,
                OrderStatus.PAID,
                paidAt,
                externalTradeNo,
                null,
                null,
                null
        );
    }

    public static OrderPaymentMarkResult refundPending(String orderNo,
                                                       String orderStatus,
                                                       OffsetDateTime paidAt,
                                                       String externalTradeNo,
                                                       String refundNo,
                                                       String refundStatus,
                                                       String reasonCode) {
        return new OrderPaymentMarkResult(
                OUTCOME_REFUND_PENDING,
                orderNo,
                orderStatus,
                paidAt,
                externalTradeNo,
                refundNo,
                refundStatus,
                reasonCode
        );
    }

    public boolean refundPending() {
        return OUTCOME_REFUND_PENDING.equals(outcome);
    }
}
