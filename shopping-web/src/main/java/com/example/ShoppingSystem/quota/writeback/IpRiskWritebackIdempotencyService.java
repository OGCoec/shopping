package com.example.ShoppingSystem.quota.writeback;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;

public interface IpRiskWritebackIdempotencyService {
    public boolean markProcessing(String eventId);

    public void clearProcessing(String eventId);
}
