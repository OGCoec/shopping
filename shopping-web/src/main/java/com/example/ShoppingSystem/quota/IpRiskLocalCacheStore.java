package com.example.ShoppingSystem.quota;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Shared local Caffeine cache for IP reputation lightweight data.
 * <p>
 * Stores score + country snapshot with bounded TTL.
 */
@Service
public class IpRiskLocalCacheStore {

    private final Cache<String, LocalRiskEntry> cache;
    private final int localTtlMinMinutes;
    private final int localTtlMaxMinutes;

    public IpRiskLocalCacheStore(@Value("${register.ip-risk-multi-level.local-max-size:100000}") long localMaxSize,
                                 @Value("${register.ip-risk-multi-level.local-ttl-min-minutes:10}") int localTtlMinMinutes,
                                 @Value("${register.ip-risk-multi-level.local-ttl-max-minutes:50}") int localTtlMaxMinutes) {
        this.localTtlMinMinutes = Math.max(1, localTtlMinMinutes);
        this.localTtlMaxMinutes = Math.max(this.localTtlMinMinutes, localTtlMaxMinutes);
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1000, localMaxSize))
                .expireAfterWrite(this.localTtlMaxMinutes, TimeUnit.MINUTES)
                .build();
    }

    public Integer getScore(String ip) {
        LocalRiskSnapshot snapshot = getRisk(ip);
        return snapshot == null ? null : snapshot.score();
    }

    public String getCountry(String ip) {
        LocalRiskSnapshot snapshot = getRisk(ip);
        return snapshot == null ? null : snapshot.country();
    }

    public LocalRiskSnapshot getRisk(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        LocalRiskEntry entry = cache.getIfPresent(ip);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtEpochMillis() <= System.currentTimeMillis()) {
            cache.invalidate(ip);
            return null;
        }
        return new LocalRiskSnapshot(entry.score(), entry.country());
    }

    public void putScore(String ip, int score) {
        putRisk(ip, score, null);
    }

    public void putRisk(String ip, int score, String country) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        int ttlRange = localTtlMaxMinutes - localTtlMinMinutes;
        int ttl = ttlRange == 0
                ? localTtlMinMinutes
                : localTtlMinMinutes + ThreadLocalRandom.current().nextInt(ttlRange + 1);
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ttl);
        cache.put(ip, new LocalRiskEntry(score, normalizeCountry(country), expiresAt));
    }

    private String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return null;
        }
        return country.trim().toUpperCase(Locale.ROOT);
    }

    private record LocalRiskEntry(int score, String country, long expiresAtEpochMillis) {
    }

    public record LocalRiskSnapshot(int score, String country) {
    }
}
