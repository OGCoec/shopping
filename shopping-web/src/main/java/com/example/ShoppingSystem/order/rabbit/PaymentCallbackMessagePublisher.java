package com.example.ShoppingSystem.order.rabbit;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentCallbackMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final PaymentCallbackRabbitProperties properties;

    public PaymentCallbackMessagePublisher(RabbitTemplate rabbitTemplate,
                                           PaymentCallbackRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(String callbackNo) {
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getRoutingKey(),
                new PaymentCallbackDispatchMessage(callbackNo, System.currentTimeMillis())
        );
    }
}
