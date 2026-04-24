package com.example.ShoppingSystem.quota;

import com.example.ShoppingSystem.mapper.IpReputationProfileMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * IP 国家查询多级链路：
 * 本地缓存 -> Redis -> DB -> BIN。
 * <p>
 * 回写规则：
 * 1) DB 命中：回写本地缓存 + Redis；
 * 2) BIN 命中：只回写本地缓存 + Redis，不回写 DB。
 */
@Service
public class IpCountryQueryService {

    private static final Logger log = LoggerFactory.getLogger(IpCountryQueryService.class);

    private static final String SOURCE_CAFFEINE = "CAFFEINE";
    private static final String SOURCE_REDIS = "REDIS";
    private static final String SOURCE_DB = "DB";
    private static final String SOURCE_BIN = "BIN";
    private static final String SOURCE_NONE = "NONE";

    private final IpCountryLocalCacheStore localCacheStore;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final IpReputationProfileMapper ipReputationProfileMapper;
    private final Ip2LocationBinCountryService ip2LocationBinCountryService;

    @Value("${register.ip-country-cache.enabled:true}")
    private boolean enabled;

    @Value("${register.ip-country-cache.redis-key-prefix:register:ip:country:}")
    private String redisKeyPrefix;

    @Value("${register.ip-country-cache.redis-ttl-minutes:360}")
    private int redisTtlMinutes;

    @Value("${register.ip-country-cache.redis-ttl-jitter-minutes:1080}")
    private int redisTtlJitterMinutes;

    public IpCountryQueryService(IpCountryLocalCacheStore localCacheStore,
                                 StringRedisTemplate stringRedisTemplate,
                                 ObjectMapper objectMapper,
                                 IpReputationProfileMapper ipReputationProfileMapper,
                                 Ip2LocationBinCountryService ip2LocationBinCountryService) {
        this.localCacheStore = localCacheStore;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ipReputationProfileMapper = ipReputationProfileMapper;
        this.ip2LocationBinCountryService = ip2LocationBinCountryService;
    }

    public CountryQueryResult queryCountry(String publicIp) {
        if (!enabled) {
            return CountryQueryResult.failed(SOURCE_NONE, "country_cache_disabled");
        }
        if (isBlank(publicIp)) {
            return CountryQueryResult.failed(SOURCE_NONE, "invalid_ip");
        }

        String ip = publicIp.trim();

        String localCountry = localCacheStore.getCountry(ip);
        if (localCountry != null) {
            return CountryQueryResult.success(localCountry, SOURCE_CAFFEINE);
        }

        String redisCountry = readCountryFromRedis(ip);
        if (redisCountry != null) {
            localCacheStore.putCountry(ip, redisCountry);
            return CountryQueryResult.success(redisCountry, SOURCE_REDIS);
        }

        String dbCountry = readCountryFromDb(ip);
        if (dbCountry != null) {
            writeCountryToCache(ip, dbCountry, SOURCE_DB);
            return CountryQueryResult.success(dbCountry, SOURCE_DB);
        }

        String binCountry = ip2LocationBinCountryService.queryCountryCode(ip);
        if (binCountry != null) {
            // BIN 命中只回写缓存，不回写 DB。
            writeCountryToCache(ip, binCountry, SOURCE_BIN);
            return CountryQueryResult.success(binCountry, SOURCE_BIN);
        }

        return CountryQueryResult.failed(SOURCE_BIN, "country_not_found");
    }

    private String readCountryFromRedis(String ip) {
        try {
            String value = stringRedisTemplate.opsForValue().get(redisKey(ip));
            if (isBlank(value)) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.startsWith("{")) {
                JsonNode root = objectMapper.readTree(trimmed);
                String country = normalizeCountryCode(readTextNode(root, "country"));
                if (country == null) {
                    country = normalizeCountryCode(readTextNode(root, "countryCode"));
                }
                return country;
            }
            return normalizeCountryCode(trimmed);
        } catch (Exception e) {
            log.debug("IP 国家 Redis 读取失败：ip={}，reason={}", ip, e.getMessage());
            return null;
        }
    }

    private String readCountryFromDb(String ip) {
        try {
            Map<String, Object> row = ip.contains(":")
                    ? ipReputationProfileMapper.findIpv6RiskCacheByIp(ip)
                    : ipReputationProfileMapper.findIpv4RiskCacheByIp(ip);
            if (row == null || row.isEmpty()) {
                return null;
            }
            return normalizeCountryCode(toStringValue(row.get("country")));
        } catch (Exception e) {
            log.debug("IP 国家 DB 读取失败：ip={}，reason={}", ip, e.getMessage());
            return null;
        }
    }

    private void writeCountryToCache(String ip, String country, String source) {
        if (isBlank(ip) || isBlank(country)) {
            return;
        }
        String normalizedCountry = normalizeCountryCode(country);
        if (normalizedCountry == null) {
            return;
        }

        localCacheStore.putCountry(ip, normalizedCountry);

        try {
            String jsonValue = objectMapper.writeValueAsString(
                    new RedisCountryCacheValue(normalizedCountry, source, System.currentTimeMillis()));
            stringRedisTemplate.opsForValue().set(redisKey(ip), jsonValue, Duration.ofMinutes(computeRedisTtlMinutes()));
        } catch (Exception e) {
            log.debug("IP 国家 Redis 回写失败：ip={}，country={}，reason={}", ip, normalizedCountry, e.getMessage());
        }
    }

    private int computeRedisTtlMinutes() {
        int min = Math.max(1, redisTtlMinutes);
        int jitter = Math.max(0, redisTtlJitterMinutes);
        if (jitter == 0) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextInt(jitter + 1);
    }

    private String redisKey(String ip) {
        return redisKeyPrefix + ip;
    }

    private String readTextNode(JsonNode root, String field) {
        if (root == null || field == null) {
            return null;
        }
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private String normalizeCountryCode(String countryCode) {
        if (isBlank(countryCode)) {
            return null;
        }
        String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 2 || "-".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record CountryQueryResult(boolean success,
                                     String country,
                                     String source,
                                     String reason) {
        public static CountryQueryResult success(String country, String source) {
            return new CountryQueryResult(true, country, source, "ok");
        }

        public static CountryQueryResult failed(String source, String reason) {
            return new CountryQueryResult(false, null, source, reason);
        }
    }

    private record RedisCountryCacheValue(String country,
                                          String source,
                                          long updatedAtEpochMillis) {
    }
}
