package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminIp2LocationQuotaKeysResponse(String redisDatabase,
                                                String quotaPrefix,
                                                long aggregateTotalQuotaCount,
                                                long realTotalQuotaCount,
                                                List<AdminIp2LocationQuotaKeyItem> keys) {
}
