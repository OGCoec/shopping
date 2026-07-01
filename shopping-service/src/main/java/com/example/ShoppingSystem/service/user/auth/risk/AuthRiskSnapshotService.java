package com.example.ShoppingSystem.service.user.auth.risk;

public interface AuthRiskSnapshotService {
    public AuthRiskSnapshot buildRiskSnapshot(String publicIp, String deviceFingerprint);
}
