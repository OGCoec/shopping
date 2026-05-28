package com.example.ShoppingSystem.product.controller;

import com.example.ShoppingSystem.product.dto.ProductCategoryRelationResponse;
import com.example.ShoppingSystem.product.dto.PublicProductCategoryTreeNodeResponse;
import com.example.ShoppingSystem.product.service.PublicProductCategoryBrowseService;
import com.example.ShoppingSystem.product.service.PublicProductCategoryRelationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shopping/api/product-categories")
public class PublicProductCategoryController {

    private final PublicProductCategoryRelationService relationService;
    private final PublicProductCategoryBrowseService browseService;

    public PublicProductCategoryController(PublicProductCategoryRelationService relationService,
                                           PublicProductCategoryBrowseService browseService) {
        this.relationService = relationService;
        this.browseService = browseService;
    }

    @GetMapping("/tree")
    public List<PublicProductCategoryTreeNodeResponse> tree() {
        return browseService.tree();
    }

    @GetMapping("/search")
    public List<PublicProductCategoryTreeNodeResponse> search(@RequestParam(required = false) String keyword) {
        return browseService.search(keyword);
    }

    @GetMapping("/{id}/relation")
    public ProductCategoryRelationResponse relation(@PathVariable Long id) {
        return relationService.getRelation(id);
    }
}
