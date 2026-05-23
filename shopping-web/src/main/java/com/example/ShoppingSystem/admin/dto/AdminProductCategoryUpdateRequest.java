package com.example.ShoppingSystem.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record AdminProductCategoryUpdateRequest(Long parentId,
                                                String name,
                                                String code,
                                                Integer sortOrder,
                                                JsonNode iconUrls,
                                                String description,
                                                String status) {
}
