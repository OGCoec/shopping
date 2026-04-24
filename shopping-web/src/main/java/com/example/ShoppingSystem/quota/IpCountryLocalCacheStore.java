package com.example.ShoppingSystem.quota;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * IP -> country 本地缓存（Caffeine）。
 * <p>
 * 只缓存国家代码，避免缓存体积膨胀。
 */
@Service
public class IpCountryLocalCacheStore {

    private final Cache<String, LocalCountryEntry> cache;
    private final int localTtlMinMinutes;
    private final int localTtlMaxMinutes;

    public IpCountryLocalCacheStore(
            @Value("${register.ip-country-cache.local-max-size:100000}") long localMaxSize,
            @Value("${register.ip-country-cache.local-ttl-min-minutes:10}") int localTtlMinMinutes,
            @Value("${register.ip-country-cache.local-ttl-max-minutes:50}") int localTtlMaxMinutes) {
        this.localTtlMinMinutes = Math.max(1, localTtlMinMinutes);
        this.localTtlMaxMinutes = Math.max(this.localTtlMinMinutes, localTtlMaxMinutes);
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1000, localMaxSize))
                .expireAfterWrite(this.localTtlMaxMinutes, TimeUnit.MINUTES)
                .build();
    }

    public String getCountry(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        LocalCountryEntry entry = cache.getIfPresent(ip);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtEpochMillis() <= System.currentTimeMillis()) {
            cache.invalidate(ip);
            return null;
        }
        return entry.country();
    }

    public void putCountry(String ip, String country) {
        if (ip == null || ip.isBlank() || country == null || country.isBlank()) {
            return;
        }
        int ttlRange = localTtlMaxMinutes - localTtlMinMinutes;
        int ttl = ttlRange == 0
                ? localTtlMinMinutes
                : localTtlMinMinutes + ThreadLocalRandom.current().nextInt(ttlRange + 1);
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ttl);
        cache.put(ip, new LocalCountryEntry(normalizeCountryCode(country), expiresAt));
    }

    private String normalizeCountryCode(String country) {
        if (country == null || country.isBlank()) {
            return null;
        }
        return country.trim().toUpperCase(Locale.ROOT);
    }

    private record LocalCountryEntry(String country, long expiresAtEpochMillis) {
    }
}
