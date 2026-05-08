package com.example.ShoppingSystem.quota;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthHashingService;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceRiskCacheInvalidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Invalidates device-score caches after the DB score changes.
 */
@Service
public class DeviceRiskCacheInvalidationService implements DeviceRiskCacheInvalidator {

    private static final String LINKED_USER_COUNT_LOCAL_CACHE_KEY_PREFIX = "linked-user-count:";

    private final DeviceRiskLocalCacheStore localCacheStore;
    private final StringRedisTemplate stringRedisTemplate;
    private final PreAuthHashingService hashingService;

    @Value("${register.device-risk-multi-level.redis-key-prefix:register:device:risk:v2:}")
    private String redisKeyPrefix;

    @Value("${register.device-linked-user-count.redis-key-prefix:register:device:linked-user-count:v1:}")
    private String linkedUserCountRedisKeyPrefix;

    public DeviceRiskCacheInvalidationService(DeviceRiskLocalCacheStore localCacheStore,
                                              StringRedisTemplate stringRedisTemplate,
                                              PreAuthHashingService hashingService) {
        this.localCacheStore = localCacheStore;
        this.stringRedisTemplate = stringRedisTemplate;
        this.hashingService = hashingService;
    }

    @Override
    public void invalidateDeviceFingerprint(String deviceFingerprint) {
        String normalizedFingerprint = StrUtil.blankToDefault(deviceFingerprint, "").trim();
        if (StrUtil.isBlank(normalizedFingerprint)) {
            return;
        }
        String fingerprintHash = hashingService.sha256(normalizedFingerprint);
        localCacheStore.invalidate(fingerprintHash);
        try {
            stringRedisTemplate.delete(redisKeyPrefix + fingerprintHash);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void invalidateDeviceLinkedUserCount(String deviceFingerprint) {
        String normalizedFingerprint = StrUtil.blankToDefault(deviceFingerprint, "").trim();
        if (StrUtil.isBlank(normalizedFingerprint)) {
            return;
        }
        String fingerprintHash = hashingService.sha256(normalizedFingerprint);
        localCacheStore.invalidate(LINKED_USER_COUNT_LOCAL_CACHE_KEY_PREFIX + fingerprintHash);
        try {
            stringRedisTemplate.delete(linkedUserCountRedisKeyPrefix + fingerprintHash);
        } catch (Exception ignored) {
        }
    }
}
