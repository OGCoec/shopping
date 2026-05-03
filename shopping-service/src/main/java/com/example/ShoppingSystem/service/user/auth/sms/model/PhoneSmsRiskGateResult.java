package com.example.ShoppingSystem.service.user.auth.sms.model;

import lombok.Builder;
import lombok.Value;

/**
 * 手机短信发送前置风控结果。
 * <p>
 * 这个对象只表达“是否允许进入短信发送”，不负责真正发短信。
 * 这样强制绑定手机号、手机号短信登录都可以复用同一套风控网关。
 */
@Value
@Builder
public class PhoneSmsRiskGateResult {

    /**
     * true 表示可以继续进入短信限流和 MQ 发送流程。
     */
    boolean allowed;

    /**
     * true 表示风险等级已经达到阻断级，例如 L6。
     */
    boolean blocked;

    /**
     * 给前端或上层服务使用的错误码。
     */
    String error;

    /**
     * 给前端展示的简短提示。
     */
    String message;

    /**
     * 本次短信风控读取到的风险等级。
     */
    String riskLevel;

    /**
     * 验证码挑战类型，例如 HCAPTCHA、TIANAI_CAPTCHA。
     */
    String challengeType;

    /**
     * 天爱验证码子类型，例如 SLIDER、ROTATE。
     */
    String challengeSubType;

    /**
     * 三方验证码前端渲染需要的 siteKey。
     */
    String challengeSiteKey;

    /**
     * 前端拉起验证码组件时使用的稳定身份。
     * 复用现有登录验证码组件时，这个值会放到 payload.email 中，
     * 但它不是邮箱，而是短信场景 + 归一化手机号组成的 challenge 标识。
     */
    String challengeIdentity;

    public static PhoneSmsRiskGateResult allowed(String riskLevel) {
        return PhoneSmsRiskGateResult.builder()
                .allowed(true)
                .riskLevel(riskLevel)
                .message("ok")
                .build();
    }

    public static PhoneSmsRiskGateResult blocked(String riskLevel, String message) {
        return PhoneSmsRiskGateResult.builder()
                .allowed(false)
                .blocked(true)
                .error("SMS_RISK_BLOCKED")
                .riskLevel(riskLevel)
                .message(message)
                .build();
    }

    public static PhoneSmsRiskGateResult challengeRequired(String riskLevel,
                                                           String challengeType,
                                                           String challengeSubType,
                                                           String challengeSiteKey,
                                                           String challengeIdentity) {
        return PhoneSmsRiskGateResult.builder()
                .allowed(false)
                .blocked(false)
                .error("SMS_CHALLENGE_REQUIRED")
                .message("Security verification is required before sending SMS.")
                .riskLevel(riskLevel)
                .challengeType(challengeType)
                .challengeSubType(challengeSubType)
                .challengeSiteKey(challengeSiteKey)
                .challengeIdentity(challengeIdentity)
                .build();
    }

    public static PhoneSmsRiskGateResult rejected(String riskLevel, String error, String message) {
        return PhoneSmsRiskGateResult.builder()
                .allowed(false)
                .blocked(false)
                .error(error)
                .riskLevel(riskLevel)
                .message(message)
                .build();
    }
}
