package com.example.ShoppingSystem.order.service;

public final class OrderStatus {

    public static final String STOCK_CONFIRMING = "STOCK_CONFIRMING";
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String CLOSING = "CLOSING";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";
    public static final String CLOSED = "CLOSED";

    private OrderStatus() {
    }
}
