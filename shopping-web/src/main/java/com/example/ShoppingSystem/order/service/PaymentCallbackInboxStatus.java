package com.example.ShoppingSystem.order.service;

import java.util.Set;

public final class PaymentCallbackInboxStatus {

    public static final String RECEIVED = "RECEIVED";
    public static final String PROCESSING = "PROCESSING";
    public static final String PROCESSED = "PROCESSED";
    public static final String FAILED = "FAILED";

    public static final Set<String> ALL = Set.of(RECEIVED, PROCESSING, PROCESSED, FAILED);

    private PaymentCallbackInboxStatus() {
    }
}
