package com.example.ShoppingSystem.admin.dto;

public record AdminIp2LocationQuotaKeyItem(String redisKey,
                                           String apiKey,
                                           String accountType,
                                           String createdAtMinute,
                                           long remainingQuota,
                                           long ttlSeconds) {
}
