package com.example.ShoppingSystem.admin.dto;

public record AdminProductSkuBatchResponse(int requestedCount,
                                           int matchedCount,
                                           int affectedCount) {
}
