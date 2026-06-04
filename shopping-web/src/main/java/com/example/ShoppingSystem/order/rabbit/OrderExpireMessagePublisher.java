package com.example.ShoppingSystem.order.rabbit;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderExpireMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final OrderExpireRabbitProperties properties;

    public OrderExpireMessagePublisher(RabbitTemplate rabbitTemplate,
                                       OrderExpireRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(OrderExpireMessage message) {
        publish(message, properties.getTtlMillis());
    }

    public void publishClosingFinalize(OrderExpireMessage message) {
        publish(message, properties.getClosingGraceMillis());
    }

    private void publish(OrderExpireMessage message, long ttlMillis) {
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getDelayRoutingKey(), message, item -> {
            item.getMessageProperties().setExpiration(String.valueOf(Math.max(1L, ttlMillis)));
            return item;
        });
    }
}
