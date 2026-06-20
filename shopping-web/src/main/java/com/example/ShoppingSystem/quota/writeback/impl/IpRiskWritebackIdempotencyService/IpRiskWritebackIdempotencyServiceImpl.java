package com.example.ShoppingSystem.quota.writeback.impl.IpRiskWritebackIdempotencyService;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

import com.example.ShoppingSystem.quota.writeback.IpRiskWritebackIdempotencyService;
import com.example.ShoppingSystem.quota.writeback.IpRiskWritebackProperties;
/**
 * Provides lightweight idempotency guard for writeback command consumption.
 */
@Service
public class IpRiskWritebackIdempotencyServiceImpl implements IpRiskWritebackIdempotencyService {

    private final StringRedisTemplate stringRedisTemplate;
    private final IpRiskWritebackProperties properties;

    public IpRiskWritebackIdempotencyServiceImpl(StringRedisTemplate stringRedisTemplate,
                                             IpRiskWritebackProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public boolean markProcessing(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        String key = properties.getIdempotencyKeyPrefix() + eventId;
        Duration ttl = Duration.ofMinutes(Math.max(1, properties.getIdempotencyTtlMinutes()));
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    public void clearProcessing(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        String key = properties.getIdempotencyKeyPrefix() + eventId;
        stringRedisTemplate.delete(key);
    }
}
