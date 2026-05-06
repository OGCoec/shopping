package com.example.ShoppingSystem.security.token;

public record RefreshSession(Long userId,
                             String tokenVersion,
                             String preAuthToken,
                             String deviceFingerprintHash,
                             String userAgentHash,
                             String currentIp,
                             String riskLevel,
                             long createdAtEpochMillis,
                             long lastRefreshAtEpochMillis) {
}
