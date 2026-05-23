package com.example.ShoppingSystem.service.user.auth.passwordreset.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetMailMessage {

    private String messageId;
    private PasswordResetMailMessageType type;
    private String email;
    private String code;
    private String resetUrl;
    private long codeExpireMinutes;
    private long linkExpireMinutes;
    private int retryCount;
    private String lastError;
    private long createdAtEpochMilli;

    public PasswordResetMailMessage nextRetry(String error) {
        return PasswordResetMailMessage.builder()
                .messageId(messageId)
                .type(type)
                .email(email)
                .code(code)
                .resetUrl(resetUrl)
                .codeExpireMinutes(codeExpireMinutes)
                .linkExpireMinutes(linkExpireMinutes)
                .retryCount(retryCount + 1)
                .lastError(error)
                .createdAtEpochMilli(createdAtEpochMilli)
                .build();
    }

    public PasswordResetMailMessage markFailed(String error) {
        return PasswordResetMailMessage.builder()
                .messageId(messageId)
                .type(type)
                .email(email)
                .code(code)
                .resetUrl(resetUrl)
                .codeExpireMinutes(codeExpireMinutes)
                .linkExpireMinutes(linkExpireMinutes)
                .retryCount(retryCount)
                .lastError(error)
                .createdAtEpochMilli(createdAtEpochMilli)
                .build();
    }
}
