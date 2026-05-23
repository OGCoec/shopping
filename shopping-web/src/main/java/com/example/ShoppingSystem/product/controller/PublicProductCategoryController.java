package com.example.ShoppingSystem.product.controller;

import com.example.ShoppingSystem.product.dto.ProductCategoryRelationResponse;
import com.example.ShoppingSystem.product.service.PublicProductCategoryRelationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/api/product-categories")
public class PublicProductCategoryController {

    private final PublicProductCategoryRelationService relationService;

    public PublicProductCategoryController(PublicProductCategoryRelationService relationService) {
        this.relationService = relationService;
    }

    @GetMapping("/{id}/relation")
    public ProductCategoryRelationResponse relation(@PathVariable Long id) {
        return relationService.getRelation(id);
    }
}
