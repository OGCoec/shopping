package com.example.ShoppingSystem.order.service;

import java.util.Set;

public final class PaymentRefundSource {

    public static final String AUTO_DETECTED = "AUTO_DETECTED";
    public static final String PAYMENT_CALLBACK = "PAYMENT_CALLBACK";
    public static final String USER_APPLY = "USER_APPLY";
    public static final String ADMIN_CREATE = "ADMIN_CREATE";

    public static final Set<String> ALL = Set.of(
            AUTO_DETECTED,
            PAYMENT_CALLBACK,
            USER_APPLY,
            ADMIN_CREATE
    );

    private PaymentRefundSource() {
    }
}
