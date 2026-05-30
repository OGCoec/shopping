package com.example.ShoppingSystem.coupon.rabbit;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class CouponClaimMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final CouponClaimRabbitProperties properties;

    public CouponClaimMessagePublisher(RabbitTemplate rabbitTemplate,
                                       CouponClaimRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(CouponClaimMessage message) {
        publishWithConfirm(properties.getRoutingKey(), message);
    }

    public void publishRetry(CouponClaimMessage message, long delayMilli) {
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRetryRoutingKey(), message, m -> {
            m.getMessageProperties().setExpiration(String.valueOf(delayMilli));
            return m;
        });
    }

    public void publishDeadLetter(CouponClaimMessage message) {
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getDeadRoutingKey(), message);
    }

    private void publishWithConfirm(String routingKey, CouponClaimMessage message) {
        rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(properties.getExchange(), routingKey, message);
            operations.waitForConfirmsOrDie(5000L);
            return true;
        });
    }
}
