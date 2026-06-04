package com.example.ShoppingSystem.order.rabbit;

public record PaymentRefundDispatchMessage(String refundNo,
                                           Long createdAtEpochMilli) {
}
