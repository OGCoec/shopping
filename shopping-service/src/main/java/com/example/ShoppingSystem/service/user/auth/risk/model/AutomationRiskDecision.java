package com.example.ShoppingSystem.service.user.auth.risk.model;

public record AutomationRiskDecision(boolean blocked,
                                     String message,
                                     String deviceReason,
                                     String ipReason,
                                     Long retryAfterMs,
                                     int devicePenaltyScore,
                                     int ipPenaltyScore,
                                     Long deviceBlockTtlMs,
                                     Long ipBlockTtlMs) {

    public static AutomationRiskDecision allow() {
        return new AutomationRiskDecision(false, "ok", "", "", null, 0, 0, null, null);
    }
}
