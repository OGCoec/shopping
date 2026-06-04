package com.example.ShoppingSystem.order.dto;

public record OrderCreateRequest(String skuId,
                                 Integer quantity,
                                 String userCouponId,
                                 String idempotencyKey) {
}
