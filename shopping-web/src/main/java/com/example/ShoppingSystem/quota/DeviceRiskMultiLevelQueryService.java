package com.example.ShoppingSystem.quota;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthHashingService;
import com.example.ShoppingSystem.mapper.RegisterRiskProfileMapper;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceL6CountingBloomDecisionService;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceRiskProfileWriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Multi-level device risk lookup pipeline: local cache -> Redis -> DB.
 * Initializes a missing device profile with the fixed bootstrap score.
 */
@Service
public class DeviceRiskMultiLevelQueryService {

    private static final int SCORE_MIN = 0;
    private static final int SCORE_MAX = 10000;
    private static final int DEFAULT_DEVICE_SCORE = 7000;

    private final DeviceRiskLocalCacheStore localCacheStore;
    private final StringRedisTemplate stringRedisTemplate;
    private final RegisterRiskProfileMapper registerRiskProfileMapper;
    private final DeviceRiskProfileWriteService deviceRiskProfileWriteService;
    private final DeviceL6CountingBloomDecisionService deviceL6CountingBloomDecisionService;
    private final PreAuthHashingService hashingService;
    private final ObjectMapper objectMapper;

    @Value("${register.device-risk-multi-level.redis-key-prefix:register:device:risk:v2:}")
    private String redisKeyPrefix;

    @Value("${register.ip-risk-multi-level.redis-ttl-minutes:60}")
    private int redisTtlMinutes;

    @Value("${register.ip-risk-multi-level.redis-ttl-jitter-minutes:120}")
    private int redisTtlJitterMinutes;

    public DeviceRiskMultiLevelQueryService(DeviceRiskLocalCacheStore localCacheStore,
                                            StringRedisTemplate stringRedisTemplate,
                                            RegisterRiskProfileMapper registerRiskProfileMapper,
                                            DeviceRiskProfileWriteService deviceRiskProfileWriteService,
                                            DeviceL6CountingBloomDecisionService deviceL6CountingBloomDecisionService,
                                            PreAuthHashingService hashingService,
                                            ObjectMapper objectMapper) {
        this.localCacheStore = localCacheStore;
        this.stringRedisTemplate = stringRedisTemplate;
        this.registerRiskProfileMapper = registerRiskProfileMapper;
        this.deviceRiskProfileWriteService = deviceRiskProfileWriteService;
        this.deviceL6CountingBloomDecisionService = deviceL6CountingBloomDecisionService;
        this.hashingService = hashingService;
        this.objectMapper = objectMapper;
    }

    public int resolveDeviceScore(String deviceFingerprint, String clientIp) {
        String normalizedFingerprint = normalizeText(deviceFingerprint);
        if (StrUtil.isBlank(normalizedFingerprint)) {
            return DEFAULT_DEVICE_SCORE;
        }

        Integer fastL6Score = deviceL6CountingBloomDecisionService.resolveFastL6ScoreIfHit(normalizedFingerprint);
        if (fastL6Score != null) {
            return clamp(fastL6Score);
        }

        String cacheKey = fingerprintCacheKey(normalizedFingerprint);

        Integer localScore = localCacheStore.getScore(cacheKey);
        if (localScore != null) {
            return clamp(localScore);
        }

        Integer redisScore = readFromRedis(cacheKey);
        if (redisScore != null) {
            int score = clamp(redisScore);
            localCacheStore.putScore(cacheKey, score);
            return score;
        }

        Integer dbScore = registerRiskProfileMapper.findDeviceRiskScoreByFingerprint(normalizedFingerprint);
        if (dbScore != null) {
            int score = clamp(dbScore);
            writeToRedis(cacheKey, score);
            localCacheStore.putScore(cacheKey, score);
            return score;
        }

        int score = clamp(deviceRiskProfileWriteService.ensureProfileExists(normalizedFingerprint, clientIp));
        writeToRedis(cacheKey, score);
        localCacheStore.putScore(cacheKey, score);
        return score;
    }

    private Integer readFromRedis(String cacheKey) {
        try {
            String value = stringRedisTemplate.opsForValue().get(redisKey(cacheKey));
            if (StrUtil.isBlank(value)) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.startsWith("{")) {
                DeviceRiskCacheValue parsed = objectMapper.readValue(trimmed, DeviceRiskCacheValue.class);
                return parsed == null ? null : parsed.score();
            }
            return Integer.parseInt(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeToRedis(String cacheKey, int score) {
        try {
            int ttl = Math.max(1, redisTtlMinutes);
            int jitter = Math.max(0, redisTtlJitterMinutes);
            int extra = jitter == 0 ? 0 : ThreadLocalRandom.current().nextInt(jitter + 1);
            stringRedisTemplate.opsForValue().set(
                    redisKey(cacheKey),
                    objectMapper.writeValueAsString(new DeviceRiskCacheValue(score)),
                    ttl + extra,
                    TimeUnit.MINUTES
            );
        } catch (Exception ignored) {
        }
    }

    private String redisKey(String cacheKey) {
        return redisKeyPrefix + cacheKey;
    }

    private String fingerprintCacheKey(String normalizedFingerprint) {
        return hashingService.sha256(normalizedFingerprint);
    }

    private int clamp(int score) {
        return Math.max(SCORE_MIN, Math.min(score, SCORE_MAX));
    }

    private String normalizeText(String value) {
        return StrUtil.blankToDefault(value, "").trim();
    }

    private record DeviceRiskCacheValue(int score) {
    }
}
