package com.example.ShoppingSystem.order.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentCallbackEvent(String callbackNo,
                                   String orderNo,
                                   String externalTradeNo,
                                   String paymentProvider,
                                   OffsetDateTime paidAt,
                                   BigDecimal paidAmountYuan) {
}
