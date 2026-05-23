package com.example.ShoppingSystem.service.user.auth.register.mq;

import com.example.ShoppingSystem.config.WelcomeMailRabbitProperties;
import com.example.ShoppingSystem.service.mail.ShoppingMailSender;
import com.example.ShoppingSystem.service.user.auth.register.WelcomeMailMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class WelcomeMailMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(WelcomeMailMessageConsumer.class);

    private final ShoppingMailSender shoppingMailSender;
    private final WelcomeMailMessagePublisher publisher;
    private final WelcomeMailRabbitProperties properties;

    public WelcomeMailMessageConsumer(ShoppingMailSender shoppingMailSender,
                                      WelcomeMailMessagePublisher publisher,
                                      WelcomeMailRabbitProperties properties) {
        this.shoppingMailSender = shoppingMailSender;
        this.publisher = publisher;
        this.properties = properties;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.welcome-mail.queue:welcome.mail.queue}",
            containerFactory = "welcomeMailRabbitListenerContainerFactory"
    )
    public void consume(WelcomeMailMessage message) {
        try {
            if (message == null || message.getEmail() == null || message.getEmail().isBlank()) {
                throw new IllegalArgumentException("Welcome mail email is required.");
            }
            shoppingMailSender.sendText(
                    message.getEmail(),
                    "欢迎使用 Shopping System",
                    "欢迎注册 Shopping System，你的账号已经创建成功。"
            );
            log.info("Welcome mail sent, messageId={}, email={}, retryCount={}",
                    message.getMessageId(), message.getEmail(), message.getRetryCount());
        } catch (Exception e) {
            log.warn("Welcome mail failed, messageId={}, email={}, retryCount={}, error={}",
                    message == null ? null : message.getMessageId(),
                    message == null ? null : message.getEmail(),
                    message == null ? null : message.getRetryCount(),
                    e.getMessage());
            handleFailure(message, e);
        }
    }

    private void handleFailure(WelcomeMailMessage message, Exception exception) {
        if (message == null) {
            return;
        }
        String errorMessage = exception.getMessage();
        if (message.getRetryCount() < properties.getMaxRetryCount()) {
            long delayMilli = resolveRetryDelayMilli(message.getRetryCount());
            publisher.publishRetry(message.nextRetry(errorMessage), delayMilli);
            return;
        }
        publisher.publishDeadLetter(message.markFailed(errorMessage));
    }

    private long resolveRetryDelayMilli(int currentRetryCount) {
        return switch (currentRetryCount) {
            case 0 -> 30_000L;
            case 1 -> 120_000L;
            default -> 300_000L;
        };
    }
}
