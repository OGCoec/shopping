package com.example.ShoppingSystem.config;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * IP2Location.io 月额度专用 Redis 配置。
 * 使用 Redis 第 3 分区（database=2）隔离额度数据。
 */
@Configuration
public class Ip2LocationQuotaRedisConfig {

    @Bean(name = "ip2LocationQuotaRedisTemplate")
    public StringRedisTemplate ip2LocationQuotaRedisTemplate(RedisProperties redisProperties) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(redisProperties.getHost());
        configuration.setPort(redisProperties.getPort());
        configuration.setDatabase(2);

        if (redisProperties.getPassword() != null) {
            configuration.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        return new StringRedisTemplate(factory);
    }
}
