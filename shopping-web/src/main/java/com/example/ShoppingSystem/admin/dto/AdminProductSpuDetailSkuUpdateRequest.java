package com.example.ShoppingSystem.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

public record AdminProductSpuDetailSkuUpdateRequest(String id,
                                                    String skuCode,
                                                    String skuName,
                                                    JsonNode specJson,
                                                    String skuImageUrl,
                                                    BigDecimal priceYuan,
                                                    BigDecimal originalPriceYuan,
                                                    Integer stockQuantity,
                                                    String status) {
}
