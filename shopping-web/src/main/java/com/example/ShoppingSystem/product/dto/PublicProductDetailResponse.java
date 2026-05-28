package com.example.ShoppingSystem.product.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record PublicProductDetailResponse(@JsonSerialize(using = ToStringSerializer.class) Long id,
                                          @JsonSerialize(using = ToStringSerializer.class) Long categoryId,
                                          String categoryName,
                                          String name,
                                          String subtitle,
                                          String brandName,
                                          String mainImageUrl,
                                          JsonNode imageUrls,
                                          JsonNode detailImageUrls,
                                          JsonNode attributes,
                                          String description,
                                          String afterSale,
                                          List<PublicProductSkuResponse> skus) {
}
