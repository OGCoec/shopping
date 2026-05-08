package com.example.ShoppingSystem.service.user.profile.mq;

import com.example.ShoppingSystem.config.UserAccountDeletionRabbitProperties;
import com.example.ShoppingSystem.service.user.profile.UserAccountDeletionMailSender;
import com.example.ShoppingSystem.service.user.profile.UserAccountDeletionMessagePublisher;
import com.example.ShoppingSystem.service.user.profile.UserAccountDeletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UserAccountDeletionMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserAccountDeletionMessageConsumer.class);

    private final UserAccountDeletionService userAccountDeletionService;
    private final UserAccountDeletionMailSender userAccountDeletionMailSender;
    private final UserAccountDeletionMessagePublisher userAccountDeletionMessagePublisher;
    private final UserAccountDeletionRabbitProperties properties;

    public UserAccountDeletionMessageConsumer(UserAccountDeletionService userAccountDeletionService,
                                              UserAccountDeletionMailSender userAccountDeletionMailSender,
                                              UserAccountDeletionMessagePublisher userAccountDeletionMessagePublisher,
                                              UserAccountDeletionRabbitProperties properties) {
        this.userAccountDeletionService = userAccountDeletionService;
        this.userAccountDeletionMailSender = userAccountDeletionMailSender;
        this.userAccountDeletionMessagePublisher = userAccountDeletionMessagePublisher;
        this.properties = properties;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.user-account-deletion.queue:user.account.deletion.queue}",
            containerFactory = "userAccountDeletionRabbitListenerContainerFactory"
    )
    public void consume(UserAccountDeletionMessage message) {
        long startedAt = System.currentTimeMillis();
        log.info("User account deletion message consumed, messageId={}, type={}, userId={}, email={}, retryCount={}",
                message == null ? null : message.getMessageId(),
                message == null ? null : message.getType(),
                message == null ? null : message.getUserId(),
                message == null ? null : message.getEmail(),
                message == null ? null : message.getRetryCount());
        try {
            if (message == null || message.getType() == null) {
                throw new IllegalArgumentException("User account deletion message type is required.");
            }
            switch (message.getType()) {
                case SELF_DELETION_REQUESTED -> handleSelfDeletionRequested(message);
                case SELF_DELETION_COMPLETED -> handleSelfDeletionCompleted(message);
            }
            log.info("User account deletion message handled, messageId={}, type={}, costMs={}",
                    message.getMessageId(), message.getType(), System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            log.warn("User account deletion message failed, messageId={}, type={}, retryCount={}, error={}",
                    message == null ? null : message.getMessageId(),
                    message == null ? null : message.getType(),
                    message == null ? null : message.getRetryCount(),
                    e.getMessage());
            handleFailure(message, e);
        }
    }

    private void handleSelfDeletionRequested(UserAccountDeletionMessage message) {
        UserAccountDeletionService.MailTarget target = userAccountDeletionService.handleSelfDeletionRequested(message);
        userAccountDeletionMailSender.sendDeletionQueued(target.email());
    }

    private void handleSelfDeletionCompleted(UserAccountDeletionMessage message) {
        userAccountDeletionMailSender.sendDeletionCompleted(message.getEmail());
    }

    private void handleFailure(UserAccountDeletionMessage message, Exception exception) {
        if (message == null) {
            return;
        }
        String errorMessage = exception.getMessage();
        if (message.getRetryCount() < properties.getMaxRetryCount()) {
            long delayMilli = resolveRetryDelayMilli(message.getRetryCount());
            userAccountDeletionMessagePublisher.publishRetry(message.nextRetry(errorMessage), delayMilli);
            return;
        }
        userAccountDeletionMessagePublisher.publishDeadLetter(message.markFailed(errorMessage));
    }

    private long resolveRetryDelayMilli(int currentRetryCount) {
        return switch (currentRetryCount) {
            case 0 -> 30_000L;
            case 1 -> 120_000L;
            default -> 300_000L;
        };
    }
}
