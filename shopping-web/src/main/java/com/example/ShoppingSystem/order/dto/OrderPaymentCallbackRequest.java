package com.example.ShoppingSystem.order.dto;

import java.time.OffsetDateTime;
import java.math.BigDecimal;

public record OrderPaymentCallbackRequest(String orderNo,
                                          String externalTradeNo,
                                          OffsetDateTime paidAt,
                                          BigDecimal paidAmountYuan,
                                          String paymentProvider) {
}
