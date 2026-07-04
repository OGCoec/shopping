package com.example.ShoppingSystem.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "商品分类", description = "前台商品分类浏览接口")
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

    @Operation(summary = "查询前台商品分类树")
    @GetMapping("/tree")
    public List<PublicProductCategoryTreeNodeResponse> tree() {
        return browseService.tree();
    }

    @Operation(summary = "搜索前台商品分类")
    @GetMapping("/search")
    public List<PublicProductCategoryTreeNodeResponse> search(@RequestParam(required = false) String keyword) {
        return browseService.search(keyword);
    }

    @Operation(summary = "查询商品分类关联信息")
    @GetMapping("/{id}/relation")
    public ProductCategoryRelationResponse relation(@PathVariable Long id) {
        return relationService.getRelation(id);
    }
}
