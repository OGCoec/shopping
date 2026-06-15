package com.example.ShoppingSystem.service.captcha.strategy;

public record CaptchaVerifyRequest(String challengeType,
                                   String challengeSubType,
                                   String captchaNamespace,
                                   String captchaUuid,
                                   String captchaCode,
                                   String remoteIp) {
}
