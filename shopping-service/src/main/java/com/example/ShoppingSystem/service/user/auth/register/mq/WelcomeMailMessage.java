package com.example.ShoppingSystem.service.user.auth.register.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WelcomeMailMessage {

    private String messageId;
    private String email;
    private int retryCount;
    private String lastError;
    private long createdAtEpochMilli;

    public WelcomeMailMessage nextRetry(String error) {
        return WelcomeMailMessage.builder()
                .messageId(messageId)
                .email(email)
                .retryCount(retryCount + 1)
                .lastError(error)
                .createdAtEpochMilli(createdAtEpochMilli)
                .build();
    }

    public WelcomeMailMessage markFailed(String error) {
        return WelcomeMailMessage.builder()
                .messageId(messageId)
                .email(email)
                .retryCount(retryCount)
                .lastError(error)
                .createdAtEpochMilli(createdAtEpochMilli)
                .build();
    }
}
