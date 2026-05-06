package com.example.ShoppingSystem.config;

import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class SnowflakeConfig {

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker(StringRedisTemplate stringRedisTemplate) {
        return new SnowflakeIdWorker(1L, 1L, stringRedisTemplate);
    }

    @Bean
    public HybridSemaphoreIdWorker hybridSemaphoreIdWorker() {
        return new HybridSemaphoreIdWorker(1L, 1L);
    }
}
