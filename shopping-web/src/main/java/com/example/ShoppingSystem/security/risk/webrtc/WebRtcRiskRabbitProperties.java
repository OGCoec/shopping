package com.example.ShoppingSystem.security.risk.webrtc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq.webrtc-risk")
public class WebRtcRiskRabbitProperties {

    private String exchange = "risk.webrtc.exchange";
    private String queue = "risk.webrtc.check.queue";
    private String retryQueue = "risk.webrtc.retry.queue";
    private String deadLetterQueue = "risk.webrtc.dlq";

    private String routingKey = "risk.webrtc.check";
    private String retryRoutingKey = "risk.webrtc.retry";
    private String deadRoutingKey = "risk.webrtc.dead";

    private int concurrency = 2;
    private int maxConcurrency = 8;
    private int prefetch = 5;
    private int maxRetryCount = 3;
}
