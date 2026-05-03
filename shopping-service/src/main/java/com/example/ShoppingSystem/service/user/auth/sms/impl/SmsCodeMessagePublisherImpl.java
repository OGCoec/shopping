package com.example.ShoppingSystem.service.user.auth.sms.impl;

import com.example.ShoppingSystem.config.SmsCodeRabbitProperties;
import com.example.ShoppingSystem.service.user.auth.sms.SmsCodeMessagePublisher;
import com.example.ShoppingSystem.service.user.auth.sms.mq.SmsCodeMessage;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class SmsCodeMessagePublisherImpl implements SmsCodeMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final SmsCodeRabbitProperties properties;

    public SmsCodeMessagePublisherImpl(RabbitTemplate rabbitTemplate,
                                       SmsCodeRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publishSmsCode(SmsCodeMessage message) {
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getRoutingKey(),
                message
        );
    }

    @Override
    public void publishRetry(SmsCodeMessage message, long delayMilli) {
        MessagePostProcessor delayProcessor = rabbitMessage -> {
            rabbitMessage.getMessageProperties().setExpiration(String.valueOf(delayMilli));
            return rabbitMessage;
        };
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getRetryRoutingKey(),
                message,
                delayProcessor
        );
    }

    @Override
    public void publishDeadLetter(SmsCodeMessage message) {
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getDeadRoutingKey(),
                message
        );
    }
}
