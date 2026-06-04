package com.example.ShoppingSystem.order.rabbit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq.payment-callback")
public class PaymentCallbackRabbitProperties {

    private String exchange = "payment.callback.exchange";
    private String queue = "payment.callback.dispatch.queue";
    private String deadLetterQueue = "payment.callback.dispatch.dlq";
    private String routingKey = "payment.callback.dispatch";
    private String deadRoutingKey = "payment.callback.dead";
    private int concurrency = 1;
    private int maxConcurrency = 4;
    private int prefetch = 10;
}
