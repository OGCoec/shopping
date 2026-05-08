package com.example.ShoppingSystem.service.user.profile;

import com.example.ShoppingSystem.service.user.profile.mq.UserAccountDeletionMessage;

import java.time.OffsetDateTime;

public interface UserAccountDeletionMessagePublisher {

    void publishSelfDeletionRequested(Long userId, String email, String deletionReason, OffsetDateTime requestedAt);

    void publishSelfDeletionCompleted(Long userId, String email);

    void publishRetry(UserAccountDeletionMessage message, long delayMilli);

    void publishDeadLetter(UserAccountDeletionMessage message);
}
