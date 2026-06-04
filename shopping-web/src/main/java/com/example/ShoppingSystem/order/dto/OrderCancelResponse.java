package com.example.ShoppingSystem.order.dto;

import java.time.OffsetDateTime;

public record OrderCancelResponse(String orderNo,
                                  String status,
                                  OffsetDateTime cancelledAt) {
}
