package com.example.ShoppingSystem.quota;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Ip2LocationQuotaBootstrapTest {

    private static final String API_KEY = "4A3230AC087F11A1D757DD5C636FCC59";
    private static final long REMAINING_QUOTA = 50_000L;

    @Test
    void shouldWriteMonthlyQuotaToRedisDb2() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName("127.0.0.1");
        configuration.setPort(6380);
        configuration.setDatabase(2);
        configuration.setPassword(RedisPassword.of("123456"));

        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();

        StringRedisTemplate redisTemplate = new StringRedisTemplate(factory);
        Ip2LocationQuotaService quotaService = new Ip2LocationQuotaService(redisTemplate);

        quotaService.initializeMonthlyQuota(API_KEY, REMAINING_QUOTA);

        String quotaKey = quotaService.buildQuotaKey(API_KEY, LocalDateTime.now());
        String quotaValue = redisTemplate.opsForValue().get(quotaKey);
        assertEquals(String.valueOf(REMAINING_QUOTA), quotaValue, "Redis 中应写入本月剩余额度");

        factory.destroy();
    }
}
