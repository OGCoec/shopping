package com.example.ShoppingSystem.service.captcha.strategy;

public record CaptchaGenerateRequest(String challengeType,
                                     String challengeSubType,
                                     String captchaNamespace,
                                     String existingCaptchaIdOrUuid) {
}
