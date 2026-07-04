package com.example.ShoppingSystem.product.service;

import com.example.ShoppingSystem.product.dto.PublicProductDetailResponse;

public interface PublicProductRuntimeStockService {

    PublicProductDetailResponse overlayRuntimeStock(PublicProductDetailResponse detail);
}
