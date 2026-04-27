package com.example.ShoppingSystem.quota.writeback;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Policy and runtime settings for IP risk writeback orchestration.
 */
@Data
@ConfigurationProperties(prefix = "register.ip-risk-writeback")
public class IpRiskWritebackProperties {

    /**
     * Master switch for writeback orchestration.
     */
    private boolean enabled = true;

    /**
     * Execution mode: SYNC / ASYNC / HYBRID.
     */
    private String mode = "ASYNC";

    /**
     * Fallback to synchronous execution when async publish fails.
     */
    private boolean fallbackSyncOnPublishFailure = true;

    /**
     * TTL for writeback idempotency records (minutes).
     */
    private int idempotencyTtlMinutes = 90;

    /**
     * Prefix for writeback idempotency keys in Redis.
     */
    private String idempotencyKeyPrefix = "register:ip:risk:writeback:idempotency:";

    /**
     * Source-to-action mapping, value format is comma-separated actions.
     */
    private Map<String, String> sourceActions = new HashMap<>();

    public IpRiskWritebackProperties() {
        sourceActions.put("API", "UPSERT_DB,WRITE_REDIS_CACHE,SYNC_BLOOM");
        sourceActions.put("DB", "UPSERT_DB,WRITE_REDIS_CACHE");
        sourceActions.put("REDIS", "");
        sourceActions.put("CAFFEINE", "");
        sourceActions.put("NONE", "");
    }
}
