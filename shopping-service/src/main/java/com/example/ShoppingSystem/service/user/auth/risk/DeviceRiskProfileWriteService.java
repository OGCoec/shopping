package com.example.ShoppingSystem.service.user.auth.risk;

public interface DeviceRiskProfileWriteService {
    public void recordSuccess(Long userId, String deviceFingerprint, String clientIp, String scene);

    public void recordFailure(Long userId, String deviceFingerprint, String clientIp, String scene);

    public int ensureProfileExists(String deviceFingerprint, String clientIp);

    public void applyAutomationPenalty(String deviceFingerprint, String clientIp, int penaltyScore, String reason);
}
