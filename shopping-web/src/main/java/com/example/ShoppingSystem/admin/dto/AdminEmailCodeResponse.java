package com.example.ShoppingSystem.admin.dto;

public record AdminEmailCodeResponse(long ttlSeconds,
                                     long cooldownSeconds) {
}
