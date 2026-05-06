package com.example.ShoppingSystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq.user-avatar")
public class UserAvatarRabbitProperties {

    private String exchange = "user.avatar.exchange";
    private String queue = "user.avatar.queue";
    private String retryQueue = "user.avatar.retry.queue";
    private String deadLetterQueue = "user.avatar.dlq";
    private String routingKey = "user.avatar.upload";
    private String retryRoutingKey = "user.avatar.retry";
    private String deadRoutingKey = "user.avatar.dead";
    private int concurrency = 1;
    private int maxConcurrency = 4;
    private int prefetch = 1;
    private int maxRetryCount = 2;
}
