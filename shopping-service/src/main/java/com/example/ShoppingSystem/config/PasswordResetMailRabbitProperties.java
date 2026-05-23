package com.example.ShoppingSystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq.password-reset-mail")
public class PasswordResetMailRabbitProperties {

    private String exchange = "password.reset.mail.exchange";
    private String queue = "password.reset.mail.queue";
    private String retryQueue = "password.reset.mail.retry.queue";
    private String deadLetterQueue = "password.reset.mail.dlq";
    private String routingKey = "password.reset.mail.send";
    private String retryRoutingKey = "password.reset.mail.retry";
    private String deadRoutingKey = "password.reset.mail.dead";
    private int concurrency = 2;
    private int maxConcurrency = 6;
    private int prefetch = 1;
    private int maxRetryCount = 2;
}
