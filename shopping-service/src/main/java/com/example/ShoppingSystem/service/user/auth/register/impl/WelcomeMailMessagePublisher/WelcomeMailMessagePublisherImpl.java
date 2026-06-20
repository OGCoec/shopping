package com.example.ShoppingSystem.service.user.auth.register.impl.WelcomeMailMessagePublisher;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.config.WelcomeMailRabbitProperties;
import com.example.ShoppingSystem.service.user.auth.register.WelcomeMailMessagePublisher;
import com.example.ShoppingSystem.service.user.auth.register.mq.WelcomeMailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class WelcomeMailMessagePublisherImpl implements WelcomeMailMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(WelcomeMailMessagePublisherImpl.class);
    private static final int MESSAGE_ID_LENGTH = 48;

    private final RabbitTemplate rabbitTemplate;
    private final WelcomeMailRabbitProperties properties;

    public WelcomeMailMessagePublisherImpl(RabbitTemplate rabbitTemplate,
                                           WelcomeMailRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publishWelcomeMail(String email) {
        WelcomeMailMessage message = WelcomeMailMessage.builder()
                .messageId(IdUtil.nanoId(MESSAGE_ID_LENGTH))
                .email(email)
                .retryCount(0)
                .createdAtEpochMilli(System.currentTimeMillis())
                .build();
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), message);
        log.info("Welcome mail message published, messageId={}, email={}", message.getMessageId(), email);
    }

    @Override
    public void publishRetry(WelcomeMailMessage message, long delayMilli) {
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
        log.warn("Welcome mail retry message published, messageId={}, email={}, retryCount={}, delayMilli={}",
                message.getMessageId(), message.getEmail(), message.getRetryCount(), delayMilli);
    }

    @Override
    public void publishDeadLetter(WelcomeMailMessage message) {
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getDeadRoutingKey(), message);
        log.error("Welcome mail dead-letter message published, messageId={}, email={}, retryCount={}",
                message.getMessageId(), message.getEmail(), message.getRetryCount());
    }
}
