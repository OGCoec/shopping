package com.example.ShoppingSystem.order.rabbit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq.refund")
public class PaymentRefundRabbitProperties {

    private String exchange = "payment.refund.exchange";
    private String queue = "payment.refund.dispatch.queue";
    private String deadLetterQueue = "payment.refund.dispatch.dlq";
    private String routingKey = "payment.refund.dispatch";
    private String deadRoutingKey = "payment.refund.dead";
    private int concurrency = 1;
    private int maxConcurrency = 4;
    private int prefetch = 10;
}
