package com.example.ShoppingSystem.admin.dto;

public record AdminProductSpuCreateRequest(Long categoryId,
                                           String name,
                                           String subtitle,
                                           String brandName,
                                           String mainImageTempUrl,
                                           String uploadSessionId,
                                           String status) {
}
