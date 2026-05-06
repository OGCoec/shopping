package com.example.ShoppingSystem.service.user.profile.mq;

import com.example.ShoppingSystem.config.UserAvatarRabbitProperties;
import com.example.ShoppingSystem.service.user.profile.UserAvatarService;
import com.example.ShoppingSystem.service.user.profile.UserAvatarUploadMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UserAvatarUploadMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserAvatarUploadMessageConsumer.class);

    private final UserAvatarService userAvatarService;
    private final UserAvatarUploadMessagePublisher userAvatarUploadMessagePublisher;
    private final UserAvatarRabbitProperties properties;

    public UserAvatarUploadMessageConsumer(UserAvatarService userAvatarService,
                                           UserAvatarUploadMessagePublisher userAvatarUploadMessagePublisher,
                                           UserAvatarRabbitProperties properties) {
        this.userAvatarService = userAvatarService;
        this.userAvatarUploadMessagePublisher = userAvatarUploadMessagePublisher;
        this.properties = properties;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.user-avatar.queue:user.avatar.queue}",
            containerFactory = "userAvatarRabbitListenerContainerFactory"
    )
    public void consumeAvatarUpload(UserAvatarUploadMessage message) {
        long startedAt = System.currentTimeMillis();
        long queueLagMs = elapsedSince(message == null ? 0L : message.getCreatedAtEpochMilli(), startedAt);
        try {
            userAvatarService.processAvatarUpload(message);
            log.info("User avatar upload consumed, userId={}, messageId={}, retryCount={}, queueLagMs={}",
                    message == null ? null : message.getUserId(),
                    message == null ? null : message.getMessageId(),
                    message == null ? null : message.getRetryCount(),
                    queueLagMs);
        } catch (Exception ex) {
            handleConsumeFailure(message, ex, queueLagMs);
        }
    }

    void handleConsumeFailure(UserAvatarUploadMessage message, Exception exception, long queueLagMs) {
        if (message == null) {
            log.error("User avatar message consume failed before payload binding, error={}", exception.getMessage(), exception);
            return;
        }

        String errorMessage = exception.getMessage();
        if (message.getRetryCount() < properties.getMaxRetryCount()) {
            long delayMilli = resolveRetryDelayMilli(message.getRetryCount());
            UserAvatarUploadMessage retryMessage = message.nextRetry(errorMessage);
            userAvatarUploadMessagePublisher.publishRetry(retryMessage, delayMilli);
            log.warn("User avatar upload retry scheduled, userId={}, messageId={}, retryCount={}, delayMilli={}, queueLagMs={}, error={}",
                    message.getUserId(), message.getMessageId(), retryMessage.getRetryCount(), delayMilli, queueLagMs, errorMessage);
            return;
        }

        userAvatarUploadMessagePublisher.publishDeadLetter(message.markFailed(errorMessage));
        log.error("User avatar upload moved to dead letter queue, userId={}, messageId={}, retryCount={}, queueLagMs={}, error={}",
                message.getUserId(), message.getMessageId(), message.getRetryCount(), queueLagMs, errorMessage);
    }

    long resolveRetryDelayMilli(int currentRetryCount) {
        return switch (currentRetryCount) {
            case 0 -> 15_000L;
            case 1 -> 60_000L;
            default -> 180_000L;
        };
    }

    private long elapsedSince(long startedAtEpochMilli, long nowEpochMilli) {
        if (startedAtEpochMilli <= 0L) {
            return -1L;
        }
        return Math.max(0L, nowEpochMilli - startedAtEpochMilli);
    }
}
