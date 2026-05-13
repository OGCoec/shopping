package com.example.ShoppingSystem.admin.dto;

public record AdminIpRiskBatchUpdateResponse(String action,
                                             int targetScore,
                                             int dbUpdatedCount,
                                             int cacheDeletedCount,
                                             int bloomSyncedCount,
                                             String message) {
}
