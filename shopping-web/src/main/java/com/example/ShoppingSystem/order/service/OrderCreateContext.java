package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.service.inventory.OrderInventoryType;

import java.time.OffsetDateTime;

public record OrderCreateContext(String orderNo,
                                 Long userId,
                                 OrderSkuSnapshot sku,
                                 int quantity,
                                 String idempotencyKey,
                                 String rawUserCouponId,
                                 OffsetDateTime now,
                                 OffsetDateTime expireAt,
                                 OrderInventoryType inventoryType) {
}
