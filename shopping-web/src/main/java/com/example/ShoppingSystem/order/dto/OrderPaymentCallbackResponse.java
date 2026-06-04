package com.example.ShoppingSystem.order.dto;

import java.time.OffsetDateTime;

public record OrderPaymentCallbackResponse(String outcome,
                                           String orderNo,
                                           String orderStatus,
                                           OffsetDateTime paidAt,
                                           String externalTradeNo,
                                           String refundNo,
                                           String refundStatus,
                                           String reasonCode) {
}
