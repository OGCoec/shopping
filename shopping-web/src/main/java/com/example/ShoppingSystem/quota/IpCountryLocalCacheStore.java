package com.example.ShoppingSystem.quota;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * IP -> geo local Caffeine cache.
 * <p>
 * Stores country/region/city/coordinates so risk checks do not repeatedly hit DB.
 */
@Service
public class IpCountryLocalCacheStore {

    private final Cache<String, LocalGeoEntry> cache;
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
        IpGeoSnapshot snapshot = getGeo(ip);
        return snapshot == null ? null : snapshot.country();
    }

    public IpGeoSnapshot getGeo(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        LocalGeoEntry entry = cache.getIfPresent(ip);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtEpochMillis() <= System.currentTimeMillis()) {
            cache.invalidate(ip);
            return null;
        }
        return new IpGeoSnapshot(
                entry.country(),
                entry.region(),
                entry.city(),
                entry.latitude(),
                entry.longitude()
        );
    }

    public void putCountry(String ip, String country) {
        putGeo(ip, new IpGeoSnapshot(country, null, null, null, null));
    }

    public void putGeo(String ip, IpGeoSnapshot geo) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        IpGeoSnapshot normalized = normalizeGeo(geo);
        if (normalized == null || !normalized.hasAnyGeo()) {
            return;
        }
        int ttlRange = localTtlMaxMinutes - localTtlMinMinutes;
        int ttl = ttlRange == 0
                ? localTtlMinMinutes
                : localTtlMinMinutes + ThreadLocalRandom.current().nextInt(ttlRange + 1);
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ttl);
        cache.put(ip, new LocalGeoEntry(
                normalized.country(),
                normalized.region(),
                normalized.city(),
                normalized.latitude(),
                normalized.longitude(),
                expiresAt
        ));
    }

    public void putGeos(Map<String, IpGeoSnapshot> geos) {
        if (geos == null || geos.isEmpty()) {
            return;
        }
        int ttlRange = localTtlMaxMinutes - localTtlMinMinutes;
        int ttl = ttlRange == 0
                ? localTtlMinMinutes
                : localTtlMinMinutes + ThreadLocalRandom.current().nextInt(ttlRange + 1);
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ttl);
        Map<String, LocalGeoEntry> entries = new LinkedHashMap<>();
        geos.forEach((ip, geo) -> {
            if (ip == null || ip.isBlank()) {
                return;
            }
            IpGeoSnapshot normalized = normalizeGeo(geo);
            if (normalized == null || !normalized.hasAnyGeo()) {
                return;
            }
            entries.put(ip, new LocalGeoEntry(
                    normalized.country(),
                    normalized.region(),
                    normalized.city(),
                    normalized.latitude(),
                    normalized.longitude(),
                    expiresAt
            ));
        });
        if (!entries.isEmpty()) {
            cache.putAll(entries);
        }
    }

    public void invalidate(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        cache.invalidate(ip);
    }

    private IpGeoSnapshot normalizeGeo(IpGeoSnapshot geo) {
        if (geo == null) {
            return null;
        }
        return new IpGeoSnapshot(
                normalizeCountryCode(geo.country()),
                normalizeNullableText(geo.region()),
                normalizeNullableText(geo.city()),
                geo.latitude(),
                geo.longitude()
        );
    }

    private String normalizeCountryCode(String country) {
        if (country == null || country.isBlank()) {
            return null;
        }
        String normalized = country.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || "-".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()
                || "-".equals(normalized)
                || "N/A".equalsIgnoreCase(normalized)
                || normalized.toLowerCase(Locale.ROOT).contains("not supported")) {
            return null;
        }
        return normalized;
    }

    private record LocalGeoEntry(String country,
                                 String region,
                                 String city,
                                 BigDecimal latitude,
                                 BigDecimal longitude,
                                 long expiresAtEpochMillis) {
    }
}
