package com.example.ShoppingSystem.order.dto;

public record OrderPaymentRequest(String externalTradeNo,
                                  String paymentType,
                                  Long loadtestDelayMillis,
                                  String loadtestFault) {
}
