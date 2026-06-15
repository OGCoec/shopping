package com.example.ShoppingSystem.order.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentStatusQueryResult(PaymentStatusQueryStatus status,
                                       OffsetDateTime paidAt,
                                       String externalTradeNo,
                                       BigDecimal paidAmountYuan,
                                       String paymentProvider) {

    public PaymentStatusQueryResult {
        status = status == null ? PaymentStatusQueryStatus.UNKNOWN : status;
    }

    public static PaymentStatusQueryResult unknown() {
        return new PaymentStatusQueryResult(PaymentStatusQueryStatus.UNKNOWN, null, null, null, null);
    }

    public static PaymentStatusQueryResult unpaid() {
        return new PaymentStatusQueryResult(PaymentStatusQueryStatus.UNPAID, null, null, null, null);
    }

    public static PaymentStatusQueryResult paid(OffsetDateTime paidAt,
                                                String externalTradeNo,
                                                BigDecimal paidAmountYuan,
                                                String paymentProvider) {
        return new PaymentStatusQueryResult(
                PaymentStatusQueryStatus.PAID,
                paidAt,
                externalTradeNo,
                paidAmountYuan,
                paymentProvider
        );
    }
}
