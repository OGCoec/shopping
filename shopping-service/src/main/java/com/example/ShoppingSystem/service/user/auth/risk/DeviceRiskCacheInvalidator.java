package com.example.ShoppingSystem.service.user.auth.risk;

/**
 * Optional device-risk cache invalidation hook.
 */
public interface DeviceRiskCacheInvalidator {

    void invalidateDeviceFingerprint(String deviceFingerprint);

    default void invalidateDeviceLinkedUserCount(String deviceFingerprint) {
    }
}
