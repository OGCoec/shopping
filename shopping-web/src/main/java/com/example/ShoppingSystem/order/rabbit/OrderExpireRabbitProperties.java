package com.example.ShoppingSystem.order.rabbit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq.order-expire")
public class OrderExpireRabbitProperties {

    private String exchange = "order.expire.exchange";
    private String delayQueue = "order.expire.delay.queue";
    private String deadLetterQueue = "order.expire.dlq";
    private String delayRoutingKey = "order.expire.delay";
    private String deadRoutingKey = "order.expire.dead";
    private long ttlMillis = 300_000L;
    private long closingGraceMillis = 300_000L;
    private int concurrency = 2;
    private int maxConcurrency = 10;
    private int prefetch = 10;
}
