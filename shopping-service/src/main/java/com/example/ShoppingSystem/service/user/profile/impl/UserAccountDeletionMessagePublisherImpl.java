package com.example.ShoppingSystem.service.user.profile.impl;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.config.UserAccountDeletionRabbitProperties;
import com.example.ShoppingSystem.service.user.profile.UserAccountDeletionMessagePublisher;
import com.example.ShoppingSystem.service.user.profile.mq.UserAccountDeletionMessage;
import com.example.ShoppingSystem.service.user.profile.mq.UserAccountDeletionMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class UserAccountDeletionMessagePublisherImpl implements UserAccountDeletionMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(UserAccountDeletionMessagePublisherImpl.class);
    private static final int MESSAGE_ID_LENGTH = 48;

    private final RabbitTemplate rabbitTemplate;
    private final UserAccountDeletionRabbitProperties properties;

    public UserAccountDeletionMessagePublisherImpl(RabbitTemplate rabbitTemplate,
                                                   UserAccountDeletionRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publishSelfDeletionRequested(Long userId,
                                             String email,
                                             String deletionReason,
                                             OffsetDateTime requestedAt) {
        UserAccountDeletionMessage message = UserAccountDeletionMessage.builder()
                .messageId(IdUtil.nanoId(MESSAGE_ID_LENGTH))
                .type(UserAccountDeletionMessageType.SELF_DELETION_REQUESTED)
                .userId(userId)
                .email(email)
                .deletionReason(deletionReason)
                .requestedAtEpochMilli(toEpochMilli(requestedAt))
                .retryCount(0)
                .createdAtEpochMilli(System.currentTimeMillis())
                .build();
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), message);
        log.info("User account deletion request message published, userId={}, email={}, messageId={}",
                userId, email, message.getMessageId());
    }

    @Override
    public void publishSelfDeletionCompleted(Long userId, String email) {
        UserAccountDeletionMessage message = UserAccountDeletionMessage.builder()
                .messageId(IdUtil.nanoId(MESSAGE_ID_LENGTH))
                .type(UserAccountDeletionMessageType.SELF_DELETION_COMPLETED)
                .userId(userId)
                .email(email)
                .retryCount(0)
                .createdAtEpochMilli(System.currentTimeMillis())
                .build();
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), message);
        log.info("User account deletion completion mail message published, userId={}, email={}, messageId={}",
                userId, email, message.getMessageId());
    }

    @Override
    public void publishRetry(UserAccountDeletionMessage message, long delayMilli) {
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
        log.warn("User account deletion retry message published, userId={}, email={}, messageId={}, retryCount={}, delayMilli={}",
                message.getUserId(), message.getEmail(), message.getMessageId(), message.getRetryCount(), delayMilli);
    }

    @Override
    public void publishDeadLetter(UserAccountDeletionMessage message) {
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getDeadRoutingKey(), message);
        log.error("User account deletion dead-letter message published, userId={}, email={}, messageId={}, retryCount={}",
                message.getUserId(), message.getEmail(), message.getMessageId(), message.getRetryCount());
    }

    private long toEpochMilli(OffsetDateTime value) {
        OffsetDateTime safeValue = value == null ? OffsetDateTime.now() : value;
        return safeValue.toInstant().toEpochMilli();
    }
}
