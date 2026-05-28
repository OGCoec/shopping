package com.example.ShoppingSystem.product.controller;

import com.example.ShoppingSystem.product.dto.PublicProductDetailResponse;
import com.example.ShoppingSystem.product.dto.PublicProductSearchResponse;
import com.example.ShoppingSystem.product.service.PublicProductDetailService;
import com.example.ShoppingSystem.product.service.PublicProductSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/search")
    public PublicProductSearchResponse search(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Long categoryId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        return productSearchService.search(keyword, categoryId, page, pageSize);
    }

    @GetMapping("/{id}")
    public PublicProductDetailResponse detail(@PathVariable String id) {
        return productDetailService.detail(id);
    }
}
