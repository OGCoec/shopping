package com.example.ShoppingSystem.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(PhoneBoundCountingBloomProperties.class)
public class PhoneBoundCountingBloomConfig {

    @Bean
    public Executor phoneBoundCountingBloomExecutor(PhoneBoundCountingBloomProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsyncCorePoolSize());
        executor.setMaxPoolSize(properties.getAsyncMaxPoolSize());
        executor.setQueueCapacity(properties.getAsyncQueueCapacity());
        executor.setThreadNamePrefix("phone-bound-cbf-");
        executor.initialize();
        return executor;
    }
}
