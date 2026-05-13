package com.example.ShoppingSystem.admin.dto;

public record AdminDeviceRiskListItemResponse(String deviceId,
                                              String fingerprintHash,
                                              String maskedFingerprint,
                                              int currentScore,
                                              String riskLevel,
                                              String firstSeenAt,
                                              String lastSeenAt,
                                              String lastLoginIp,
                                              int linkedUserCount,
                                              int recentDistinctIpCount,
                                              int recentIpSwitchCount,
                                              String lastPenaltyReason,
                                              int lastPenaltyScore,
                                              String lastPenaltyAt) {
}
