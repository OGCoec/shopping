package com.example.ShoppingSystem.service.user.auth.register.impl;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.config.RegisterEmailCodeRabbitProperties;
import com.example.ShoppingSystem.service.user.auth.register.RegisterEmailCodeMessagePublisher;
import com.example.ShoppingSystem.service.user.auth.register.mq.RegisterEmailCodeMessage;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * 注册邮箱验证码 RabbitMQ 消息发布器实现。
 * 负责把正常发送、延迟重试和死信转储这三类消息统一封装成 RabbitMQ 投递动作。
 */
@Service
public class RegisterEmailCodeMessagePublisherImpl implements RegisterEmailCodeMessagePublisher {

    private static final int MESSAGE_ID_LENGTH = 48;

    private final RabbitTemplate rabbitTemplate;
    private final RegisterEmailCodeRabbitProperties properties;

    /**
     * 注入 RabbitTemplate 和注册邮箱验证码 RabbitMQ 配置。
     *
     * @param rabbitTemplate Spring AMQP RabbitTemplate
     * @param properties 注册邮箱验证码 RabbitMQ 配置
     */
    public RegisterEmailCodeMessagePublisherImpl(RabbitTemplate rabbitTemplate,
                                                 RegisterEmailCodeRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    /**
     * 发布注册邮箱验证码正常发送消息。
     * 这里会创建新的消息 ID，并把首次投递的 retryCount 固定为 0。
     *
     * @param email 收件人邮箱
     * @param code 验证码文本
     * @param expireMinutes 验证码有效期（分钟）
     */
    @Override
    public void publishRegisterEmailCode(String email, String code, long expireMinutes) {
        RegisterEmailCodeMessage message = RegisterEmailCodeMessage.builder()
                .messageId(IdUtil.nanoId(MESSAGE_ID_LENGTH))
                .email(email)
                .code(code)
                .expireMinutes(expireMinutes)
                .retryCount(0)
                .createdAtEpochMilli(System.currentTimeMillis())
                .build();
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getRoutingKey(),
                message
        );
    }

    /**
     * 发布注册邮箱验证码重试消息。
     * 重试消息会先进入重试队列，并通过 per-message TTL 延迟一段时间后再回流主队列。
     *
     * @param message 需要重新投递的注册邮箱验证码消息
     * @param delayMilli 本次重试延迟毫秒数
     */
    @Override
    public void publishRetry(RegisterEmailCodeMessage message, long delayMilli) {
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

    /**
     * 发布注册邮箱验证码死信消息。
     * 当消费者已经达到最大重试次数时，把失败消息投递到死信队列，便于后续排查。
     *
     * @param message 已经达到最大重试次数的注册邮箱验证码消息
     */
    @Override
    public void publishDeadLetter(RegisterEmailCodeMessage message) {
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getDeadRoutingKey(),
                message
        );
    }
}
