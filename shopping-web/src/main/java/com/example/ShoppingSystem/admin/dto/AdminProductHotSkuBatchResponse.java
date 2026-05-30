package com.example.ShoppingSystem.admin.dto;

public record AdminProductHotSkuBatchResponse(int requestedCount,
                                              int matchedCount,
                                              int affectedCount) {
}
