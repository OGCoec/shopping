package com.example.ShoppingSystem.admin.dto;

public record AdminIp2LocationQuotaBatchResult(int requestedCount,
                                               int affectedCount,
                                               int replacedOldCount,
                                               long totalQuotaCount) {
}
