package com.example.ShoppingSystem.controller.login.user.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 注册前置验证与邮箱验证码发送响应。
 */
@Data
@Builder
public class RegisterSendEmailCodeResponse {

    private boolean success;
    private String message;
    private Integer totalScore;
    private String riskLevel;
    private String challengeType;
    private String challengeSubType;
    private String challengeSiteKey;
    /**
     * Remaining wait time in milliseconds for OPERATION_TIMEOUT.
     */
    private Long retryAfterMs;
    /**
     * Absolute wait-until epoch millis for OPERATION_TIMEOUT.
     */
    private Long waitUntilEpochMs;
    /**
     * Remaining wait time in milliseconds before the register email code can be resent.
     */
    private Long emailCodeRetryAfterMs;
    /**
     * Next allowed register email-code send epoch millis. Named passedAt for the Redis/API contract.
     */
    private Long passedAt;
    private boolean emailCodeSent;
    /**
     * 是否在邮箱验证码通过后强制补录手机号。
     */
    private boolean requirePhoneBinding;
}
