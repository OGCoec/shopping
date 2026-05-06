package com.example.ShoppingSystem.service.user.profile.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAvatarUploadMessage {

    private String messageId;
    private Long userId;
    private String originalFilename;
    private String contentType;
    private byte[] fileBytes;
    private int retryCount;
    private long createdAtEpochMilli;
    private String lastError;

    public UserAvatarUploadMessage nextRetry(String errorMessage) {
        return UserAvatarUploadMessage.builder()
                .messageId(messageId)
                .userId(userId)
                .originalFilename(originalFilename)
                .contentType(contentType)
                .fileBytes(fileBytes)
                .retryCount(retryCount + 1)
                .createdAtEpochMilli(createdAtEpochMilli)
                .lastError(errorMessage)
                .build();
    }

    public UserAvatarUploadMessage markFailed(String errorMessage) {
        return UserAvatarUploadMessage.builder()
                .messageId(messageId)
                .userId(userId)
                .originalFilename(originalFilename)
                .contentType(contentType)
                .fileBytes(fileBytes)
                .retryCount(retryCount)
                .createdAtEpochMilli(createdAtEpochMilli)
                .lastError(errorMessage)
                .build();
    }
}
