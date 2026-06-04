package com.example.ShoppingSystem.order.dto;

import java.time.OffsetDateTime;

public record OrderPaymentResponse(String orderNo,
                                   String status,
                                   OffsetDateTime paidAt,
                                   String externalTradeNo) {
}
