package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class PaymentCallbackPendingMarkerService {

    private final StringRedisTemplate stringRedisTemplate;
    private final PaymentCallbackStreamProperties properties;

    public PaymentCallbackPendingMarkerService(StringRedisTemplate stringRedisTemplate,
                                               PaymentCallbackStreamProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public void markReceived(String orderNo, String callbackNo) {
        String normalizedOrderNo = normalize(orderNo);
        if (normalizedOrderNo.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                OrderRedisKeys.paymentCallbackReceivedOrderKey(normalizedOrderNo),
                normalize(callbackNo),
                markerTtl()
        );
    }

    public boolean hasReceived(String orderNo) {
        String normalizedOrderNo = normalize(orderNo);
        if (normalizedOrderNo.isEmpty()) {
            return false;
        }
        Boolean exists = stringRedisTemplate.hasKey(OrderRedisKeys.paymentCallbackReceivedOrderKey(normalizedOrderNo));
        return Boolean.TRUE.equals(exists);
    }

    private Duration markerTtl() {
        return Duration.ofMillis(Math.max(1L, properties.getReceivedOrderMarkerTtlMillis()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
