package com.example.ShoppingSystem.service.user.profile.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountDeletionMessage {

    private String messageId;
    private UserAccountDeletionMessageType type;
    private Long userId;
    private String email;
    private String deletionReason;
    private Long requestedAtEpochMilli;
    private int retryCount;
    private String lastError;
    private long createdAtEpochMilli;

    public UserAccountDeletionMessage nextRetry(String error) {
        return UserAccountDeletionMessage.builder()
                .messageId(messageId)
                .type(type)
                .userId(userId)
                .email(email)
                .deletionReason(deletionReason)
                .requestedAtEpochMilli(requestedAtEpochMilli)
                .retryCount(retryCount + 1)
                .lastError(error)
                .createdAtEpochMilli(createdAtEpochMilli)
                .build();
    }

    public UserAccountDeletionMessage markFailed(String error) {
        return UserAccountDeletionMessage.builder()
                .messageId(messageId)
                .type(type)
                .userId(userId)
                .email(email)
                .deletionReason(deletionReason)
                .requestedAtEpochMilli(requestedAtEpochMilli)
                .retryCount(retryCount)
                .lastError(error)
                .createdAtEpochMilli(createdAtEpochMilli)
                .build();
    }
}
