package com.example.ShoppingSystem.service.user.profile.impl;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.config.UserAvatarRabbitProperties;
import com.example.ShoppingSystem.service.user.profile.UserAvatarUploadMessagePublisher;
import com.example.ShoppingSystem.service.user.profile.mq.UserAvatarUploadMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserAvatarUploadMessagePublisherImpl implements UserAvatarUploadMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(UserAvatarUploadMessagePublisherImpl.class);
    private static final int MESSAGE_ID_LENGTH = 48;

    private final RabbitTemplate rabbitTemplate;
    private final UserAvatarRabbitProperties properties;

    public UserAvatarUploadMessagePublisherImpl(RabbitTemplate rabbitTemplate,
                                                UserAvatarRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publishAvatarUpload(Long userId, String originalFilename, String contentType, byte[] fileBytes) {
        UserAvatarUploadMessage message = UserAvatarUploadMessage.builder()
                .messageId(IdUtil.nanoId(MESSAGE_ID_LENGTH))
                .userId(userId)
                .originalFilename(originalFilename)
                .contentType(contentType)
                .fileBytes(fileBytes)
                .retryCount(0)
                .createdAtEpochMilli(System.currentTimeMillis())
                .build();
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getRoutingKey(),
                message
        );
        log.info("User avatar upload message published, userId={}, messageId={}, exchange={}, routingKey={}",
                userId, message.getMessageId(), properties.getExchange(), properties.getRoutingKey());
    }

    @Override
    public void publishRetry(UserAvatarUploadMessage message, long delayMilli) {
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
        log.warn("User avatar retry message published, userId={}, messageId={}, retryCount={}, delayMilli={}",
                message.getUserId(), message.getMessageId(), message.getRetryCount(), delayMilli);
    }

    @Override
    public void publishDeadLetter(UserAvatarUploadMessage message) {
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getDeadRoutingKey(),
                message
        );
        log.error("User avatar dead-letter message published, userId={}, messageId={}, retryCount={}",
                message.getUserId(), message.getMessageId(), message.getRetryCount());
    }
}
