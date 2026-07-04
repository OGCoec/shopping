package com.example.ShoppingSystem.product.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

public record PublicProductSkuResponse(String id,
                                       String skuName,
                                       JsonNode specJson,
                                       JsonNode skuImageUrls,
                                       BigDecimal priceYuan,
                                       BigDecimal originalPriceYuan,
                                       Integer stockQuantity,
                                       Integer remainingQuantity,
                                       Boolean hotSku) {
}
