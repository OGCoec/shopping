package com.example.ShoppingSystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "login.phone-verified-user-counting-bloom")
public class PhoneVerifiedUserCountingBloomProperties {

    private boolean enabled = true;
    private String key = "login:phone:verified-user:cbf";
    private String memberKeyPrefix = "login:phone:verified-user:member:";
    private String readyKey = "login:phone:verified-user:cbf:ready";
    private String cacheKeyPrefix = "login:phone:verified-user:cache:";
    private int redisPositiveTtlMinutes = 1440;
    private int redisNegativeTtlMinutes = 5;
    private int localCacheTtlMinutes = 5;
    private long localCacheMaximumSize = 200000;
}
