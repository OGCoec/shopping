package com.example.ShoppingSystem.service.user.auth.register.model;

import com.example.ShoppingSystem.common.result.FailureType;
import lombok.Builder;
import lombok.Data;

/**
 * 注册前置验证结果。
 */
@Data
@Builder
public class RegisterPrecheckResult {

    private boolean success;
    private String message;
    private FailureType failureType;
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
    private boolean emailCodeSent;
    /**
     * 是否在邮箱验证码通过后，强制进入手机号补录步骤。
     */
    private boolean requirePhoneBinding;
}
