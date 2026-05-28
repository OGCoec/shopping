package com.example.ShoppingSystem.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record AdminProductCategoryTreeNodeResponse(@JsonSerialize(using = ToStringSerializer.class) Long id,
                                                   @JsonSerialize(using = ToStringSerializer.class) Long parentId,
                                                   String name,
                                                   String code,
                                                   Integer level,
                                                   String path,
                                                   Integer sortOrder,
                                                   JsonNode iconUrls,
                                                   String description,
                                                   String status,
                                                   Boolean isLeaf,
                                                   Integer childCount,
                                                   Integer productCount,
                                                   Integer activeProductCount,
                                                   String nameHighlight,
                                                   List<AdminProductCategoryTreeNodeResponse> children) {
}
