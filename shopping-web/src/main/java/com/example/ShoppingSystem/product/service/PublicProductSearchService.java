package com.example.ShoppingSystem.product.service;
import com.example.ShoppingSystem.product.dto.PublicProductSearchResponse;
public interface PublicProductSearchService {
    public PublicProductSearchResponse search(String keyword, Long categoryId, int page, int pageSize);
}
