package com.example.ShoppingSystem.admin.dto;

public record AdminProductHotSkuEnableItem(String skuId,
                                           Integer stockQuantity,
                                           String status,
                                           String startAt,
                                           String endAt) {
}
