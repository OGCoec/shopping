package com.example.ShoppingSystem.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(TerminatedAccountEmailCountingBloomProperties.class)
public class TerminatedAccountEmailCountingBloomConfig {

    @Bean
    public Executor terminatedAccountEmailCountingBloomExecutor(TerminatedAccountEmailCountingBloomProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsyncCorePoolSize());
        executor.setMaxPoolSize(properties.getAsyncMaxPoolSize());
        executor.setQueueCapacity(properties.getAsyncQueueCapacity());
        executor.setThreadNamePrefix("terminated-email-cbf-");
        executor.initialize();
        return executor;
    }
}
