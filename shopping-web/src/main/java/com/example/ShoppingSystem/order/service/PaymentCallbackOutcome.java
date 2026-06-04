package com.example.ShoppingSystem.order.service;

import java.util.Set;

public final class PaymentCallbackOutcome {

    public static final String PAID = "PAID";
    public static final String PAID_IDEMPOTENT = "PAID_IDEMPOTENT";
    public static final String REFUND_PENDING = "REFUND_PENDING";
    public static final String FAILED = "FAILED";

    public static final Set<String> ALL = Set.of(PAID, PAID_IDEMPOTENT, REFUND_PENDING, FAILED);

    private PaymentCallbackOutcome() {
    }
}
