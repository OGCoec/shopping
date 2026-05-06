package com.example.ShoppingSystem.quota;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Shared local Caffeine cache for device risk score snapshots.
 */
@Service
public class DeviceRiskLocalCacheStore {

    private final Cache<String, LocalDeviceRiskEntry> cache;
    private final int localTtlMinMinutes;
    private final int localTtlMaxMinutes;

    public DeviceRiskLocalCacheStore(
            @Value("${register.ip-risk-multi-level.local-max-size:100000}") long localMaxSize,
            @Value("${register.ip-risk-multi-level.local-ttl-min-minutes:10}") int localTtlMinMinutes,
            @Value("${register.ip-risk-multi-level.local-ttl-max-minutes:50}") int localTtlMaxMinutes) {
        this.localTtlMinMinutes = Math.max(1, localTtlMinMinutes);
        this.localTtlMaxMinutes = Math.max(this.localTtlMinMinutes, localTtlMaxMinutes);
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1000, localMaxSize))
                .expireAfterWrite(this.localTtlMaxMinutes, TimeUnit.MINUTES)
                .build();
    }

    public Integer getScore(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        LocalDeviceRiskEntry entry = cache.getIfPresent(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtEpochMillis() <= System.currentTimeMillis()) {
            cache.invalidate(key);
            return null;
        }
        return entry.score();
    }

    public void putScore(String key, int score) {
        if (key == null || key.isBlank()) {
            return;
        }
        int ttlRange = localTtlMaxMinutes - localTtlMinMinutes;
        int ttl = ttlRange == 0
                ? localTtlMinMinutes
                : localTtlMinMinutes + ThreadLocalRandom.current().nextInt(ttlRange + 1);
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ttl);
        cache.put(key, new LocalDeviceRiskEntry(score, expiresAt));
    }

    public void invalidate(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        cache.invalidate(key);
    }

    private record LocalDeviceRiskEntry(int score, long expiresAtEpochMillis) {
    }
}
