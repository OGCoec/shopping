package com.example.ShoppingSystem.coupon.rabbit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq.coupon-claim")
public class CouponClaimRabbitProperties {

    private String exchange = "coupon.claim.exchange";
    private String queue = "coupon.claim.queue";
    private String retryQueue = "coupon.claim.retry.queue";
    private String deadLetterQueue = "coupon.claim.dlq";
    private String routingKey = "coupon.claim.create";
    private String retryRoutingKey = "coupon.claim.retry";
    private String deadRoutingKey = "coupon.claim.dead";
    private int concurrency = 2;
    private int maxConcurrency = 10;
    private int prefetch = 10;
    private int maxRetryCount = 3;
}
