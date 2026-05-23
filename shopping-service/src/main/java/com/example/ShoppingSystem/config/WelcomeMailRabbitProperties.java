package com.example.ShoppingSystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq.welcome-mail")
public class WelcomeMailRabbitProperties {

    private String exchange = "welcome.mail.exchange";
    private String queue = "welcome.mail.queue";
    private String retryQueue = "welcome.mail.retry.queue";
    private String deadLetterQueue = "welcome.mail.dlq";
    private String routingKey = "welcome.mail.send";
    private String retryRoutingKey = "welcome.mail.retry";
    private String deadRoutingKey = "welcome.mail.dead";
    private int concurrency = 1;
    private int maxConcurrency = 2;
    private int prefetch = 1;
    private int maxRetryCount = 2;
}
