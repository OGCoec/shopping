package com.example.ShoppingSystem.service.user.auth.sms.mq;

import com.example.ShoppingSystem.Utils.AliyunUtils;
import com.example.ShoppingSystem.config.SmsCodeRabbitProperties;
import com.example.ShoppingSystem.service.user.auth.sms.SmsCodeMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SmsCodeMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(SmsCodeMessageConsumer.class);

    private final AliyunUtils aliyunUtils;
    private final SmsCodeMessagePublisher smsCodeMessagePublisher;
    private final SmsCodeRabbitProperties properties;

    public SmsCodeMessageConsumer(AliyunUtils aliyunUtils,
                                  SmsCodeMessagePublisher smsCodeMessagePublisher,
                                  SmsCodeRabbitProperties properties) {
        this.aliyunUtils = aliyunUtils;
        this.smsCodeMessagePublisher = smsCodeMessagePublisher;
        this.properties = properties;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.sms-code.queue:sms.code.queue}",
            containerFactory = "smsCodeRabbitListenerContainerFactory"
    )
    public void consumeSmsCode(SmsCodeMessage message) {
        try {
            aliyunUtils.sendSmsVerifyCode(
                    message.getPhoneNumber(),
                    message.getTemplateCode(),
                    message.getCode(),
                    String.valueOf(message.getExpireMinutes())
            );
        } catch (Exception e) {
            handleConsumeFailure(message, e);
        }
    }

    void handleConsumeFailure(SmsCodeMessage message, Exception exception) {
        String errorMessage = exception.getMessage();
        if (message.getRetryCount() < properties.getMaxRetryCount()) {
            long delayMilli = resolveRetryDelayMilli(message.getRetryCount());
            SmsCodeMessage retryMessage = message.nextRetry(errorMessage);
            smsCodeMessagePublisher.publishRetry(retryMessage, delayMilli);
            log.warn("SMS message retry scheduled, messageId={}, phone={}, retryCount={}, delayMilli={}, error={}",
                    message.getMessageId(), message.getPhoneNumber(), retryMessage.getRetryCount(), delayMilli, errorMessage);
            return;
        }

        SmsCodeMessage deadLetterMessage = message.markFailed(errorMessage);
        smsCodeMessagePublisher.publishDeadLetter(deadLetterMessage);
        log.error("SMS message moved to dead letter queue, messageId={}, phone={}, retryCount={}, error={}",
                message.getMessageId(), message.getPhoneNumber(), message.getRetryCount(), errorMessage);
    }

    long resolveRetryDelayMilli(int currentRetryCount) {
        return switch (currentRetryCount) {
            case 0 -> 30_000L;
            case 1 -> 120_000L;
            default -> 300_000L;
        };
    }
}
