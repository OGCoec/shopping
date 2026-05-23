package com.example.ShoppingSystem.admin.dto;

public record AdminProductCategoryBatchDisableResponse(int requestedCount,
                                                       int rootCount,
                                                       int subtreeCount,
                                                       int affectedCount) {
}
