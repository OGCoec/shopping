package com.example.ShoppingSystem.order.rabbit;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentRefundMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final PaymentRefundRabbitProperties properties;

    public PaymentRefundMessagePublisher(RabbitTemplate rabbitTemplate,
                                         PaymentRefundRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(String refundNo) {
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getRoutingKey(),
                new PaymentRefundDispatchMessage(refundNo, System.currentTimeMillis())
        );
    }
}
