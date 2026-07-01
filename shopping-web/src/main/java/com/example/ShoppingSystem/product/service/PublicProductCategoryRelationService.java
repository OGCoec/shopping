package com.example.ShoppingSystem.product.service;
import com.example.ShoppingSystem.product.dto.ProductCategoryRelationResponse;
public interface PublicProductCategoryRelationService {
    public ProductCategoryRelationResponse getRelation(Long id);

    public void evictAfterCommit();
}
