package com.example.ShoppingSystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq.sms-code")
public class SmsCodeRabbitProperties {

    private String exchange = "sms.code.exchange";
    private String queue = "sms.code.queue";
    private String retryQueue = "sms.code.retry.queue";
    private String deadLetterQueue = "sms.code.dlq";
    private String routingKey = "sms.code.send";
    private String retryRoutingKey = "sms.code.retry";
    private String deadRoutingKey = "sms.code.dead";
    private int concurrency = 2;
    private int maxConcurrency = 10;
    private int prefetch = 1;
    private int maxRetryCount = 2;
}
