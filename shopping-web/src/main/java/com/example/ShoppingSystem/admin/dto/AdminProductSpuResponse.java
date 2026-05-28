package com.example.ShoppingSystem.admin.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.OffsetDateTime;

public record AdminProductSpuResponse(@JsonSerialize(using = ToStringSerializer.class) Long id,
                                      @JsonSerialize(using = ToStringSerializer.class) Long categoryId,
                                      String categoryName,
                                      String name,
                                      String subtitle,
                                      String brandName,
                                      String mainImageUrl,
                                      String status,
                                      OffsetDateTime createdAt,
                                      OffsetDateTime updatedAt,
                                      String nameHighlight) {
}
