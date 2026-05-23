package com.example.ShoppingSystem.product.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record ProductCategoryRelationResponse(CategorySelf self,
                                              String parent,
                                              List<String> children) {

    public record CategorySelf(String id,
                               String name,
                               String code,
                               Integer level,
                               JsonNode iconUrls,
                               String status) {
    }
}
