package com.example.ShoppingSystem.quota.writeback;

/**
 * Supported writeback actions for IP reputation data.
 */
public enum IpRiskWritebackAction {
    UPSERT_DB,
    WRITE_REDIS_CACHE,
    SYNC_BLOOM,
    WARM_LOCAL_CACHE
}
