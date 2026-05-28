package com.example.ShoppingSystem.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

public record AdminProductSpuDetailSkuResponse(String id,
                                               @JsonSerialize(using = ToStringSerializer.class) Long spuId,
                                               String skuCode,
                                               String skuName,
                                               JsonNode specJson,
                                               JsonNode skuImageUrls,
                                               BigDecimal priceYuan,
                                               BigDecimal originalPriceYuan,
                                               Integer stockQuantity,
                                               String status) {
}
