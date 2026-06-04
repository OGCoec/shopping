package com.example.ShoppingSystem.order.service;

import java.util.Set;

public final class PaymentRefundStatus {

    public static final String REFUND_PENDING = "REFUND_PENDING";
    public static final String REFUNDING = "REFUNDING";
    public static final String REFUND_APPROVED = "REFUND_APPROVED";
    public static final String REFUND_REJECTED = "REFUND_REJECTED";
    public static final String REFUNDED = "REFUNDED";
    public static final String REFUND_FAILED = "REFUND_FAILED";
    public static final String REFUND_CANCELLED = "REFUND_CANCELLED";

    public static final Set<String> ALL = Set.of(
            REFUND_PENDING,
            REFUNDING,
            REFUND_APPROVED,
            REFUND_REJECTED,
            REFUNDED,
            REFUND_FAILED,
            REFUND_CANCELLED
    );

    private PaymentRefundStatus() {
    }
}
