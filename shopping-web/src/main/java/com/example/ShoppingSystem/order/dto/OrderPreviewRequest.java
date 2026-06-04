package com.example.ShoppingSystem.order.dto;

public record OrderPreviewRequest(String skuId,
                                  Integer quantity,
                                  String selectedUserCouponId) {
}
