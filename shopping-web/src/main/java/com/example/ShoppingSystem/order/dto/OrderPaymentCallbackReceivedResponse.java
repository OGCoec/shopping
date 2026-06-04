package com.example.ShoppingSystem.order.dto;

public record OrderPaymentCallbackReceivedResponse(String callbackNo,
                                                   String orderNo,
                                                   String externalTradeNo,
                                                   String status) {
}
