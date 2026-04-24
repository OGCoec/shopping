package com.example.ShoppingSystem.quota.writeback;

/**
 * Writeback execution mode.
 */
public enum IpRiskWritebackMode {
    SYNC,
    ASYNC,
    HYBRID;

    public static IpRiskWritebackMode fromString(String mode) {
        if (mode == null || mode.isBlank()) {
            return ASYNC;
        }
        try {
            return IpRiskWritebackMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return ASYNC;
        }
    }
}
