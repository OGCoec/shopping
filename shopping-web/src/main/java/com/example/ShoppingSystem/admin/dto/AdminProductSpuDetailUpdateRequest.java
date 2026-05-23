package com.example.ShoppingSystem.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record AdminProductSpuDetailUpdateRequest(Long categoryId,
                                                 String subtitle,
                                                 String brandName,
                                                 String mainImageUrl,
                                                 String status,
                                                 JsonNode imageUrls,
                                                 JsonNode detailImageUrls,
                                                 JsonNode attributes,
                                                 String description,
                                                 String afterSale,
                                                 List<AdminProductSpuDetailSkuUpdateRequest> skus,
                                                 List<AdminProductImageUsageRequest> imageUploadSessions) {
}
