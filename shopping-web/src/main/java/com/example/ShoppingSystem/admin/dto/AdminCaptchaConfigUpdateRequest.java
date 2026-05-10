package com.example.ShoppingSystem.admin.dto;

public record AdminCaptchaConfigUpdateRequest(String siteKey,
                                              String secretKey) {
}
