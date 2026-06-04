package com.example.ShoppingSystem.order.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderCreateResponse(String orderNo,
                                  String status,
                                  OffsetDateTime expireAt,
                                  BigDecimal payAmountYuan) {
}
