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

    private final DeviceRiskLocalCacheStore localCacheStore;
    private final StringRedisTemplate stringRedisTemplate;
    private final PreAuthHashingService hashingService;

    @Value("${register.device-risk-multi-level.redis-key-prefix:register:device:risk:v2:}")
    private String redisKeyPrefix;

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
}
