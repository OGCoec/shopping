package com.example.ShoppingSystem.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public HttpClient aiHttpClient(AiProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getGatewayTimeoutMillis())))
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService aiSseExecutor() {
        return Executors.newFixedThreadPool(4, task -> {
            Thread thread = new Thread(task);
            thread.setName("shopping-ai-sse-" + thread.threadId());
            thread.setDaemon(true);
            return thread;
        });
    }
}
