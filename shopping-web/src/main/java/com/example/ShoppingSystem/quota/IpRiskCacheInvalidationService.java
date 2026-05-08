package com.example.ShoppingSystem.quota;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.service.user.auth.risk.IpRiskCacheInvalidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class IpRiskCacheInvalidationService implements IpRiskCacheInvalidator {

    private final IpRiskLocalCacheStore localCacheStore;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${register.ip-risk-multi-level.redis-key-prefix:register:ip:risk:v2:}")
    private String redisKeyPrefix;

    public IpRiskCacheInvalidationService(IpRiskLocalCacheStore localCacheStore,
                                          StringRedisTemplate stringRedisTemplate) {
        this.localCacheStore = localCacheStore;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void invalidateIp(String ip) {
        String normalizedIp = StrUtil.blankToDefault(ip, "").trim();
        if (StrUtil.isBlank(normalizedIp)) {
            return;
        }
        localCacheStore.invalidate(normalizedIp);
        try {
            stringRedisTemplate.delete(redisKeyPrefix + normalizedIp);
        } catch (Exception ignored) {
        }
    }
}
