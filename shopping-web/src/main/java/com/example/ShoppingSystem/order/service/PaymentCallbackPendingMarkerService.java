package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;

public interface PaymentCallbackPendingMarkerService {
    public void markReceived(String orderNo, String callbackNo);

    public boolean hasReceived(String orderNo);
}
