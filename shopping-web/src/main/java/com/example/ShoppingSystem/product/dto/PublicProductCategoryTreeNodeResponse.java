package com.example.ShoppingSystem.product.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record PublicProductCategoryTreeNodeResponse(String id,
                                                    String name,
                                                    String nameHighlight,
                                                    String code,
                                                    Integer level,
                                                    JsonNode iconUrls,
                                                    List<PublicProductCategoryTreeNodeResponse> children) {
}
