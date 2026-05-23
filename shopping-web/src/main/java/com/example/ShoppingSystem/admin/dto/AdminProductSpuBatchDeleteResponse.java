package com.example.ShoppingSystem.admin.dto;

public record AdminProductSpuBatchDeleteResponse(int requestedCount,
                                                 int matchedCount,
                                                 int deletedSpuCount,
                                                 int deletedSkuCount,
                                                 int deletedDetailCount,
                                                 int cleanupQueuedCount) {
}
