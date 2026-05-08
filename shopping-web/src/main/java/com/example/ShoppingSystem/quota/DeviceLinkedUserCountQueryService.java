package com.example.ShoppingSystem.quota;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthHashingService;
import com.example.ShoppingSystem.mapper.RegisterRiskProfileMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Multi-level lookup pipeline for the number of users linked to one device.
 */
@Service
public class DeviceLinkedUserCountQueryService {

    private static final String LOCAL_CACHE_KEY_PREFIX = "linked-user-count:";

    private final DeviceRiskLocalCacheStore localCacheStore;
    private final StringRedisTemplate stringRedisTemplate;
    private final RegisterRiskProfileMapper registerRiskProfileMapper;
    private final PreAuthHashingService hashingService;
    private final ObjectMapper objectMapper;

    @Value("${register.device-linked-user-count.redis-key-prefix:register:device:linked-user-count:v1:}")
    private String redisKeyPrefix;

    @Value("${register.device-linked-user-count.redis-ttl-minutes:60}")
    private int redisTtlMinutes;

    @Value("${register.device-linked-user-count.redis-ttl-jitter-minutes:120}")
    private int redisTtlJitterMinutes;

    public DeviceLinkedUserCountQueryService(DeviceRiskLocalCacheStore localCacheStore,
                                             StringRedisTemplate stringRedisTemplate,
                                             RegisterRiskProfileMapper registerRiskProfileMapper,
                                             PreAuthHashingService hashingService,
                                             ObjectMapper objectMapper) {
        this.localCacheStore = localCacheStore;
        this.stringRedisTemplate = stringRedisTemplate;
        this.registerRiskProfileMapper = registerRiskProfileMapper;
        this.hashingService = hashingService;
        this.objectMapper = objectMapper;
    }

    public int resolveLinkedUserCount(String deviceFingerprint) {
        String normalizedFingerprint = normalizeText(deviceFingerprint);
        if (StrUtil.isBlank(normalizedFingerprint)) {
            return 0;
        }

        String fingerprintHash = hashingService.sha256(normalizedFingerprint);
        String localCacheKey = localCacheKey(fingerprintHash);

        Integer localCount = localCacheStore.getScore(localCacheKey);
        if (localCount != null) {
            return normalizeCount(localCount);
        }

        Integer redisCount = readFromRedis(fingerprintHash);
        if (redisCount != null) {
            int count = normalizeCount(redisCount);
            localCacheStore.putScore(localCacheKey, count);
            return count;
        }

        Integer profileCount = registerRiskProfileMapper.findLinkedUserCountByFingerprint(normalizedFingerprint);
        if (profileCount != null) {
            int count = normalizeCount(profileCount);
            writeToRedis(fingerprintHash, count);
            localCacheStore.putScore(localCacheKey, count);
            return count;
        }

        int fallbackCount = normalizeCount(registerRiskProfileMapper.countLinkedUsersByFingerprint(normalizedFingerprint));
        writeToRedis(fingerprintHash, fallbackCount);
        localCacheStore.putScore(localCacheKey, fallbackCount);
        return fallbackCount;
    }

    private Integer readFromRedis(String fingerprintHash) {
        try {
            String value = stringRedisTemplate.opsForValue().get(redisKey(fingerprintHash));
            if (StrUtil.isBlank(value)) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.startsWith("{")) {
                LinkedUserCountCacheValue parsed = objectMapper.readValue(trimmed, LinkedUserCountCacheValue.class);
                return parsed == null ? null : parsed.count();
            }
            return Integer.parseInt(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeToRedis(String fingerprintHash, int count) {
        try {
            int ttl = Math.max(1, redisTtlMinutes);
            int jitter = Math.max(0, redisTtlJitterMinutes);
            int extra = jitter == 0 ? 0 : ThreadLocalRandom.current().nextInt(jitter + 1);
            stringRedisTemplate.opsForValue().set(
                    redisKey(fingerprintHash),
                    objectMapper.writeValueAsString(new LinkedUserCountCacheValue(count)),
                    ttl + extra,
                    TimeUnit.MINUTES
            );
        } catch (Exception ignored) {
        }
    }

    private String localCacheKey(String fingerprintHash) {
        return LOCAL_CACHE_KEY_PREFIX + fingerprintHash;
    }

    private String redisKey(String fingerprintHash) {
        return redisKeyPrefix + fingerprintHash;
    }

    private int normalizeCount(int count) {
        return Math.max(0, count);
    }

    private String normalizeText(String value) {
        return StrUtil.blankToDefault(value, "").trim();
    }

    private record LinkedUserCountCacheValue(int count) {
    }
}
