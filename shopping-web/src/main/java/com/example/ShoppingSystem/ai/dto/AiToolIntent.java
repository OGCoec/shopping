package com.example.ShoppingSystem.ai.dto;

public record AiToolIntent(AiToolIntentType intent,
                           String query,
                           Long productId,
                           String skuId,
                           String couponTemplateId,
                           Long categoryId,
                           Integer page,
                           Integer pageSize) {
}
