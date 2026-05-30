package com.example.ShoppingSystem.admin.dto;

import java.time.OffsetDateTime;

public record AdminProductHotSkuResponse(String id,
                                         Long spuId,
                                         String skuId,
                                         String skuCode,
                                         String skuName,
                                         Integer skuStockQuantity,
                                         String skuStatus,
                                         Integer stockQuantity,
                                         Integer remainingQuantity,
                                         String status,
                                         OffsetDateTime startAt,
                                         OffsetDateTime endAt,
                                         Long version,
                                         OffsetDateTime createdAt,
                                         OffsetDateTime updatedAt) {
}
