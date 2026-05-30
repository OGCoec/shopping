package com.example.ShoppingSystem.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

public record AdminProductSkuCreateRequest(String skuCode,
                                           String skuName,
                                           JsonNode specJson,
                                           JsonNode skuImageUrls,
                                           BigDecimal priceYuan,
                                           BigDecimal originalPriceYuan,
                                           Integer stockQuantity,
                                           String status,
                                           List<AdminProductImageUsageRequest> imageUploadSessions) {
}
