package com.example.ShoppingSystem.product.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record PublicProductSummaryResponse(@JsonSerialize(using = ToStringSerializer.class) Long id,
                                           @JsonSerialize(using = ToStringSerializer.class) Long categoryId,
                                           String categoryName,
                                           String name,
                                           String nameHighlight,
                                           String subtitle,
                                           String brandName,
                                           String mainImageUrl) {
}
