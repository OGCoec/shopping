package com.example.ShoppingSystem.quota.writeback;

import com.example.ShoppingSystem.mapper.IpReputationProfileMapper;
import com.example.ShoppingSystem.quota.IpRiskCachedPayload;
import com.example.ShoppingSystem.quota.IpRiskLocalCacheStore;
import com.example.ShoppingSystem.service.user.auth.register.risk.impl.IpL6CountingBloomDecisionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Executes IP risk writeback actions.
 */
@Service
public class IpRiskWritebackExecutorService {

    private static final Logger log = LoggerFactory.getLogger(IpRiskWritebackExecutorService.class);

    private final IpReputationProfileMapper ipReputationProfileMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService;
    private final IpRiskLocalCacheStore localCacheStore;

    @Value("${register.ip-risk-multi-level.redis-key-prefix:register:ip:risk:v2:}")
    private String redisKeyPrefix;

    @Value("${register.ip-risk-multi-level.redis-ttl-minutes:60}")
    private int redisTtlMinutes;

    @Value("${register.ip-risk-multi-level.redis-ttl-jitter-minutes:120}")
    private int redisTtlJitterMinutes;

    @Value("${register.ip-risk-multi-level.db-expire-hours:24}")
    private int dbExpireHours;

    public IpRiskWritebackExecutorService(IpReputationProfileMapper ipReputationProfileMapper,
                                          StringRedisTemplate stringRedisTemplate,
                                          ObjectMapper objectMapper,
                                          IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService,
                                          IpRiskLocalCacheStore localCacheStore) {
        this.ipReputationProfileMapper = ipReputationProfileMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ipL6CountingBloomDecisionService = ipL6CountingBloomDecisionService;
        this.localCacheStore = localCacheStore;
    }

    public void executeActions(String ip, IpRiskCachedPayload payload, Set<IpRiskWritebackAction> actions) {
        if (ip == null || ip.isBlank() || payload == null || actions == null || actions.isEmpty()) {
            return;
        }

        if (actions.contains(IpRiskWritebackAction.UPSERT_DB)) {
            upsertToDb(ip, payload);
        }
        if (actions.contains(IpRiskWritebackAction.WRITE_REDIS_CACHE)) {
            writeToRedis(ip, payload.currentScore(), payload.country());
        }
        if (actions.contains(IpRiskWritebackAction.SYNC_BLOOM)) {
            syncBloom(ip, payload.currentScore());
        }
        if (actions.contains(IpRiskWritebackAction.WARM_LOCAL_CACHE)) {
            localCacheStore.putRisk(ip, payload.currentScore(), payload.country());
        }
    }

    private void upsertToDb(String ip, IpRiskCachedPayload payload) {
        try {
            long queriedAtMillis = payload.queriedAtEpochMillis() > 0
                    ? payload.queriedAtEpochMillis()
                    : System.currentTimeMillis();
            long expiresAtMillis = payload.expiresAtEpochMillis() > queriedAtMillis
                    ? payload.expiresAtEpochMillis()
                    : queriedAtMillis + TimeUnit.HOURS.toMillis(Math.max(1, dbExpireHours));
            OffsetDateTime queriedAt = Instant.ofEpochMilli(queriedAtMillis).atOffset(ZoneOffset.UTC);
            OffsetDateTime expiresAt = Instant.ofEpochMilli(expiresAtMillis).atOffset(ZoneOffset.UTC);
            String rawJson = objectMapper.writeValueAsString(payload);
            String ipType = resolveIpType(payload.usageType(), payload.proxyIsDataCenter());

            if (ip.contains(":")) {
                ipReputationProfileMapper.upsertIpv6RiskProfile(
                        ip,
                        ipType,
                        payload.country(),
                        payload.asn(),
                        payload.providerName(),
                        payload.latitude(),
                        payload.longitude(),
                        payload.proxyIsDataCenter(),
                        payload.proxyIsVpn(),
                        payload.isProxy(),
                        payload.proxyIsTor(),
                        payload.fraudScore(),
                        payload.currentScore(),
                        payload.currentScore(),
                        payload.currentScore(),
                        payload.sourceProvider(),
                        rawJson,
                        queriedAt,
                        expiresAt
                );
            } else {
                ipReputationProfileMapper.upsertIpv4RiskProfile(
                        ip,
                        ipType,
                        payload.country(),
                        payload.asn(),
                        payload.providerName(),
                        payload.latitude(),
                        payload.longitude(),
                        payload.proxyIsDataCenter(),
                        payload.proxyIsVpn(),
                        payload.isProxy(),
                        payload.proxyIsTor(),
                        payload.fraudScore(),
                        payload.currentScore(),
                        payload.currentScore(),
                        payload.currentScore(),
                        payload.sourceProvider(),
                        rawJson,
                        queriedAt,
                        expiresAt
                );
            }
        } catch (JsonProcessingException e) {
            log.warn("IP risk writeback DB failed due to payload serialization, ip={}", ip);
        } catch (Exception e) {
            log.warn("IP risk writeback DB failed, ip={}, reason={}", ip, e.getMessage());
        }
    }

    private void writeToRedis(String ip, int score, String country) {
        try {
            int ttl = Math.max(1, redisTtlMinutes);
            int jitter = Math.max(0, redisTtlJitterMinutes);
            int extra = jitter == 0 ? 0 : ThreadLocalRandom.current().nextInt(jitter + 1);
            String cacheValue = objectMapper.writeValueAsString(
                    new RedisRiskCacheValue(
                            score,
                            normalizeCountryCode(country)));
            stringRedisTemplate.opsForValue().set(redisKey(ip), cacheValue, ttl + extra, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("IP risk writeback Redis failed, ip={}, reason={}", ip, e.getMessage());
        }
    }

    private void syncBloom(String ip, int score) {
        try {
            ipL6CountingBloomDecisionService.syncMembershipByScore(ip, score);
        } catch (Exception e) {
            log.warn("IP risk bloom sync failed, ip={}, score={}, reason={}", ip, score, e.getMessage());
        }
    }

    private String redisKey(String ip) {
        return redisKeyPrefix + ip;
    }

    private String normalizeCountryCode(String country) {
        if (country == null || country.isBlank()) {
            return null;
        }
        String normalized = country.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private record RedisRiskCacheValue(int score, String country) {
    }

    private String resolveIpType(String usageType, boolean isDataCenter) {
        if ("RESIDENTIAL".equals(usageType)) {
            return "RESIDENTIAL";
        }
        if ("MOBILE".equals(usageType)) {
            return "MOBILE";
        }
        if ("BUSINESS".equals(usageType)) {
            return "BUSINESS";
        }
        if ("DCH".equals(usageType) || isDataCenter) {
            return "DATACENTER";
        }
        return "UNKNOWN";
    }
}
