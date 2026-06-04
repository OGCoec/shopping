package com.example.ShoppingSystem.order.service;

public record OrderInventoryItem(String orderNo,
                                 Long userId,
                                 byte[] skuId,
                                 String skuIdText,
                                 int quantity,
                                 boolean hotSku) {
}
