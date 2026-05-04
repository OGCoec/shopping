package com.example.ShoppingSystem.service.user.auth.register.mq;

import com.example.ShoppingSystem.config.RegisterEmailCodeRabbitProperties;
import com.example.ShoppingSystem.service.user.auth.register.RegisterEmailCodeMailSender;
import com.example.ShoppingSystem.service.user.auth.register.RegisterEmailCodeMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 注册邮箱验证码 RabbitMQ 消费者。
 * 负责消费发信任务、调用 MailUtils 真正发送邮件，并在失败时执行有限次数重试或转入死信队列。
 */
@Component
public class RegisterEmailCodeMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(RegisterEmailCodeMessageConsumer.class);

    private final RegisterEmailCodeMailSender registerEmailCodeMailSender;
    private final RegisterEmailCodeMessagePublisher registerEmailCodeMessagePublisher;
    private final RegisterEmailCodeRabbitProperties properties;

    /**
     * 注入发信工具、消息发布器和 RabbitMQ 配置。
     *
     * @param mailUtils 邮件发送工具
     * @param registerEmailCodeMessagePublisher 注册邮箱验证码消息发布器
     * @param properties 注册邮箱验证码 RabbitMQ 配置
     */
    public RegisterEmailCodeMessageConsumer(RegisterEmailCodeMailSender registerEmailCodeMailSender,
                                            RegisterEmailCodeMessagePublisher registerEmailCodeMessagePublisher,
                                            RegisterEmailCodeRabbitProperties properties) {
        this.registerEmailCodeMailSender = registerEmailCodeMailSender;
        this.registerEmailCodeMessagePublisher = registerEmailCodeMessagePublisher;
        this.properties = properties;
    }

    /**
     * 消费注册邮箱验证码发送消息。
     * 成功时直接完成消费；失败时根据 retryCount 决定是重新投递到重试队列，还是转入死信队列。
     *
     * @param message 当前消费到的注册邮箱验证码消息
     */
    @RabbitListener(
            queues = "${app.rabbitmq.register-email.queue:register.email.code.queue}",
            containerFactory = "registerEmailCodeRabbitListenerContainerFactory"
    )
    public void consumeRegisterEmailCode(RegisterEmailCodeMessage message) {
        long consumeStartedAt = System.currentTimeMillis();
        long queueLagMs = elapsedSince(message.getCreatedAtEpochMilli(), consumeStartedAt);
        log.info("Register email message consumed, messageId={}, email={}, retryCount={}, queueLagMs={}, lastError={}",
                message.getMessageId(), message.getEmail(), message.getRetryCount(), queueLagMs, message.getLastError());
        try {
            long smtpStartedAt = System.currentTimeMillis();
            registerEmailCodeMailSender.sendRegisterEmailCode(
                    message.getEmail(),
                    message.getCode(),
                    message.getExpireMinutes()
            );
            long smtpDurationMs = System.currentTimeMillis() - smtpStartedAt;
            long totalAgeMs = elapsedSince(message.getCreatedAtEpochMilli(), System.currentTimeMillis());
            log.info("Register email message sent, messageId={}, email={}, retryCount={}, queueLagMs={}, smtpDurationMs={}, totalAgeMs={}",
                    message.getMessageId(), message.getEmail(), message.getRetryCount(), queueLagMs, smtpDurationMs, totalAgeMs);
        } catch (Exception e) {
            long smtpDurationMs = System.currentTimeMillis() - consumeStartedAt;
            long totalAgeMs = elapsedSince(message.getCreatedAtEpochMilli(), System.currentTimeMillis());
            log.warn("Register email message send failed, messageId={}, email={}, retryCount={}, queueLagMs={}, smtpDurationMs={}, totalAgeMs={}, error={}",
                    message.getMessageId(), message.getEmail(), message.getRetryCount(), queueLagMs, smtpDurationMs, totalAgeMs, e.getMessage());
            handleConsumeFailure(message, e, queueLagMs, smtpDurationMs, totalAgeMs);
        }
    }

    /**
     * 处理注册邮箱验证码消息消费失败场景。
     * 未到最大重试次数时，按预设延迟重新入队；达到上限后转入死信队列。
     *
     * @param message 当前失败的注册邮箱验证码消息
     * @param exception 本次失败抛出的异常
     */
    void handleConsumeFailure(RegisterEmailCodeMessage message, Exception exception) {
        handleConsumeFailure(message, exception, -1L, -1L, -1L);
    }

    void handleConsumeFailure(RegisterEmailCodeMessage message,
                              Exception exception,
                              long queueLagMs,
                              long smtpDurationMs,
                              long totalAgeMs) {
        String errorMessage = exception.getMessage();
        if (message.getRetryCount() < properties.getMaxRetryCount()) {
            long delayMilli = resolveRetryDelayMilli(message.getRetryCount());
            RegisterEmailCodeMessage retryMessage = message.nextRetry(errorMessage);
            registerEmailCodeMessagePublisher.publishRetry(retryMessage, delayMilli);
            log.warn("Register email message retry scheduled, messageId={}, email={}, retryCount={}, delayMilli={}, queueLagMs={}, smtpDurationMs={}, totalAgeMs={}, error={}",
                    message.getMessageId(), message.getEmail(), retryMessage.getRetryCount(), delayMilli, queueLagMs, smtpDurationMs, totalAgeMs, errorMessage);
            return;
        }

        RegisterEmailCodeMessage deadLetterMessage = message.markFailed(errorMessage);
        registerEmailCodeMessagePublisher.publishDeadLetter(deadLetterMessage);
        log.error("Register email message moved to dead letter queue, messageId={}, email={}, retryCount={}, queueLagMs={}, smtpDurationMs={}, totalAgeMs={}, error={}",
                message.getMessageId(), message.getEmail(), message.getRetryCount(), queueLagMs, smtpDurationMs, totalAgeMs, errorMessage);
    }

    private long elapsedSince(long startedAtEpochMilli, long nowEpochMilli) {
        if (startedAtEpochMilli <= 0L) {
            return -1L;
        }
        return Math.max(0L, nowEpochMilli - startedAtEpochMilli);
    }

    /**
     * 根据当前重试次数返回下一次重试延迟。
     * 第一次失败等待 30 秒，第二次失败等待 2 分钟，后续统一等待 5 分钟。
     *
     * @param currentRetryCount 当前消息已经发生过的重试次数
     * @return 下一次重试延迟毫秒数
     */
    long resolveRetryDelayMilli(int currentRetryCount) {
        return switch (currentRetryCount) {
            case 0 -> 30_000L;
            case 1 -> 120_000L;
            default -> 300_000L;
        };
    }
}
