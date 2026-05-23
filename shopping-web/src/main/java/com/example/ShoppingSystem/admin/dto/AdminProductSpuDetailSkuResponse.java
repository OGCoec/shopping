package com.example.ShoppingSystem.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record AdminProductSpuDetailSkuResponse(@JsonSerialize(using = ToStringSerializer.class) Long id,
                                               @JsonSerialize(using = ToStringSerializer.class) Long spuId,
                                               String skuCode,
                                               String skuName,
                                               JsonNode specJson,
                                               String skuImageUrl,
                                               Long priceCent,
                                               Long originalPriceCent,
                                               Integer stockQuantity,
                                               String status) {
}
