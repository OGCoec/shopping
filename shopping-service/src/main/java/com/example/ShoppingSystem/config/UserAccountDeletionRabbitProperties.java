package com.example.ShoppingSystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq.user-account-deletion")
public class UserAccountDeletionRabbitProperties {

    private String exchange = "user.account.deletion.exchange";
    private String queue = "user.account.deletion.queue";
    private String retryQueue = "user.account.deletion.retry.queue";
    private String deadLetterQueue = "user.account.deletion.dlq";
    private String routingKey = "user.account.deletion.handle";
    private String retryRoutingKey = "user.account.deletion.retry";
    private String deadRoutingKey = "user.account.deletion.dead";
    private int concurrency = 1;
    private int maxConcurrency = 4;
    private int prefetch = 1;
    private int maxRetryCount = 2;
}
