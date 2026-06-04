package com.example.ShoppingSystem.order.rabbit;

public record PaymentCallbackDispatchMessage(String callbackNo,
                                             Long createdAtEpochMilli) {
}
