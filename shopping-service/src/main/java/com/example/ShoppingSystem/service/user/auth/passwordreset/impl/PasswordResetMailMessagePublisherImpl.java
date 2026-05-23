package com.example.ShoppingSystem.service.user.auth.passwordreset.impl;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.config.PasswordResetMailRabbitProperties;
import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetMailMessagePublisher;
import com.example.ShoppingSystem.service.user.auth.passwordreset.mq.PasswordResetMailMessage;
import com.example.ShoppingSystem.service.user.auth.passwordreset.mq.PasswordResetMailMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetMailMessagePublisherImpl implements PasswordResetMailMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailMessagePublisherImpl.class);
    private static final int MESSAGE_ID_LENGTH = 48;

    private final RabbitTemplate rabbitTemplate;
    private final PasswordResetMailRabbitProperties properties;

    public PasswordResetMailMessagePublisherImpl(RabbitTemplate rabbitTemplate,
                                                 PasswordResetMailRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publishResetCode(String email, String code, long expireMinutes) {
        publish(PasswordResetMailMessage.builder()
                .type(PasswordResetMailMessageType.RESET_CODE)
                .email(email)
                .code(code)
                .codeExpireMinutes(expireMinutes)
                .build());
    }

    @Override
    public void publishResetLink(String email, String resetUrl, long expireMinutes) {
        publish(PasswordResetMailMessage.builder()
                .type(PasswordResetMailMessageType.RESET_LINK)
                .email(email)
                .resetUrl(resetUrl)
                .linkExpireMinutes(expireMinutes)
                .build());
    }

    @Override
    public void publishResetCodeAndLink(String email,
                                        String code,
                                        String resetUrl,
                                        long codeExpireMinutes,
                                        long linkExpireMinutes) {
        publish(PasswordResetMailMessage.builder()
                .type(PasswordResetMailMessageType.RESET_CODE_AND_LINK)
                .email(email)
                .code(code)
                .resetUrl(resetUrl)
                .codeExpireMinutes(codeExpireMinutes)
                .linkExpireMinutes(linkExpireMinutes)
                .build());
    }

    @Override
    public void publishRetry(PasswordResetMailMessage message, long delayMilli) {
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
        log.warn("Password reset mail retry message published, messageId={}, type={}, email={}, retryCount={}, delayMilli={}",
                message.getMessageId(), message.getType(), message.getEmail(), message.getRetryCount(), delayMilli);
    }

    @Override
    public void publishDeadLetter(PasswordResetMailMessage message) {
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getDeadRoutingKey(), message);
        log.error("Password reset mail dead-letter message published, messageId={}, type={}, email={}, retryCount={}",
                message.getMessageId(), message.getType(), message.getEmail(), message.getRetryCount());
    }

    private void publish(PasswordResetMailMessage source) {
        PasswordResetMailMessage message = PasswordResetMailMessage.builder()
                .messageId(IdUtil.nanoId(MESSAGE_ID_LENGTH))
                .type(source.getType())
                .email(source.getEmail())
                .code(source.getCode())
                .resetUrl(source.getResetUrl())
                .codeExpireMinutes(source.getCodeExpireMinutes())
                .linkExpireMinutes(source.getLinkExpireMinutes())
                .retryCount(0)
                .createdAtEpochMilli(System.currentTimeMillis())
                .build();
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), message);
        log.info("Password reset mail message published, messageId={}, type={}, email={}",
                message.getMessageId(), message.getType(), message.getEmail());
    }
}
