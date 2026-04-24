package com.example.ShoppingSystem.config;

import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class SnowflakeConfig {

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker(StringRedisTemplate stringRedisTemplate) {
        return new SnowflakeIdWorker(1L, 1L, stringRedisTemplate);
    }
}
