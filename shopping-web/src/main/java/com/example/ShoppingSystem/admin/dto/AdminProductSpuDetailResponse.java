package com.example.ShoppingSystem.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminProductSpuDetailResponse(@JsonSerialize(using = ToStringSerializer.class) Long id,
                                            @JsonSerialize(using = ToStringSerializer.class) Long categoryId,
                                            String categoryName,
                                            String name,
                                            String subtitle,
                                            String brandName,
                                            String mainImageUrl,
                                            String status,
                                            OffsetDateTime createdAt,
                                            OffsetDateTime updatedAt,
                                            JsonNode imageUrls,
                                            JsonNode detailImageUrls,
                                            JsonNode attributes,
                                            String description,
                                            String afterSale,
                                            List<AdminProductSpuDetailSkuResponse> skus) {
}
