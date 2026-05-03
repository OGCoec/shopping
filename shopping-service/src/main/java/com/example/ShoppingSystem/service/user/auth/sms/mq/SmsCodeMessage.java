package com.example.ShoppingSystem.service.user.auth.sms.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsCodeMessage {

    private String messageId;
    private String phoneNumber;
    private String templateCode;
    private String code;
    private long expireMinutes;
    private String scene;
    private int retryCount;
    private long createdAtEpochMilli;
    private String lastError;

    public SmsCodeMessage nextRetry(String errorMessage) {
        return SmsCodeMessage.builder()
                .messageId(messageId)
                .phoneNumber(phoneNumber)
                .templateCode(templateCode)
                .code(code)
                .expireMinutes(expireMinutes)
                .scene(scene)
                .retryCount(retryCount + 1)
                .createdAtEpochMilli(createdAtEpochMilli)
                .lastError(errorMessage)
                .build();
    }

    public SmsCodeMessage markFailed(String errorMessage) {
        return SmsCodeMessage.builder()
                .messageId(messageId)
                .phoneNumber(phoneNumber)
                .templateCode(templateCode)
                .code(code)
                .expireMinutes(expireMinutes)
                .scene(scene)
                .retryCount(retryCount)
                .createdAtEpochMilli(createdAtEpochMilli)
                .lastError(errorMessage)
                .build();
    }
}
