package com.example.ShoppingSystem.quota.writeback;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RabbitMQ settings for asynchronous IP risk writeback.
 */
@Data
@ConfigurationProperties(prefix = "app.rabbitmq.ip-risk-writeback")
public class IpRiskWritebackRabbitProperties {

    private String exchange = "ip.risk.writeback.exchange";
    private String queue = "ip.risk.writeback.queue";
    private String retryQueue = "ip.risk.writeback.retry.queue";
    private String deadLetterQueue = "ip.risk.writeback.dlq";

    private String routingKey = "ip.risk.writeback.execute";
    private String retryRoutingKey = "ip.risk.writeback.retry";
    private String deadRoutingKey = "ip.risk.writeback.dead";

    private int concurrency = 2;
    private int maxConcurrency = 10;
    private int prefetch = 5;
    private int maxRetryCount = 3;
}
