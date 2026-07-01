package com.example.ShoppingSystem.quota;

public interface DeviceRiskMultiLevelQueryService {
    public int resolveDeviceScore(String deviceFingerprint, String clientIp);
}
