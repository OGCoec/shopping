package com.example.ShoppingSystem.order.service;

import java.util.Set;

public final class PaymentRefundReasonCode {

    public static final String PAID_AFTER_ORDER_CLOSED = "PAID_AFTER_ORDER_CLOSED";
    public static final String PAID_AFTER_ORDER_CANCELLED = "PAID_AFTER_ORDER_CANCELLED";
    public static final String ORDER_NOT_FOUND_AFTER_PAID = "ORDER_NOT_FOUND_AFTER_PAID";
    public static final String FULFILLMENT_FAILED = "FULFILLMENT_FAILED";
    public static final String USER_NOT_RECEIVED_GOODS = "USER_NOT_RECEIVED_GOODS";
    public static final String DUPLICATE_PAYMENT = "DUPLICATE_PAYMENT";
    public static final String ADMIN_MANUAL = "ADMIN_MANUAL";
    public static final String OTHER = "OTHER";

    public static final Set<String> ALL = Set.of(
            PAID_AFTER_ORDER_CLOSED,
            PAID_AFTER_ORDER_CANCELLED,
            ORDER_NOT_FOUND_AFTER_PAID,
            FULFILLMENT_FAILED,
            USER_NOT_RECEIVED_GOODS,
            DUPLICATE_PAYMENT,
            ADMIN_MANUAL,
            OTHER
    );

    public static final Set<String> USER_APPLY_ALLOWED = Set.of(
            USER_NOT_RECEIVED_GOODS,
            FULFILLMENT_FAILED,
            OTHER
    );

    private PaymentRefundReasonCode() {
    }
}
