package com.example.ShoppingSystem.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record AdminProductSpuDetailSkuUpdateRequest(Long id,
                                                    String skuCode,
                                                    String skuName,
                                                    JsonNode specJson,
                                                    String skuImageUrl,
                                                    Long priceCent,
                                                    Long originalPriceCent,
                                                    Integer stockQuantity,
                                                    String status) {
}
