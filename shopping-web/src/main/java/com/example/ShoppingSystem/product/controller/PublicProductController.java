package com.example.ShoppingSystem.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.product.dto.PublicProductDetailResponse;
import com.example.ShoppingSystem.product.dto.PublicProductSearchResponse;
import com.example.ShoppingSystem.product.service.PublicProductDetailService;
import com.example.ShoppingSystem.product.service.PublicProductSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "商品", description = "前台商品查询接口")
@RestController
@RequestMapping("/shopping/api/products")
public class PublicProductController {

    private final PublicProductSearchService productSearchService;
    private final PublicProductDetailService productDetailService;

    public PublicProductController(PublicProductSearchService productSearchService,
                                   PublicProductDetailService productDetailService) {
        this.productSearchService = productSearchService;
        this.productDetailService = productDetailService;
    }

    @Operation(summary = "搜索前台商品")
    @GetMapping("/search")
    public PublicProductSearchResponse search(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Long categoryId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        return productSearchService.search(keyword, categoryId, page, pageSize);
    }

    @Operation(summary = "查询前台商品详情")
    @GetMapping("/{id}")
    public PublicProductDetailResponse detail(@PathVariable String id) {
        return productDetailService.detail(id);
    }
}
