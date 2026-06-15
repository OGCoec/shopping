package com.example.ShoppingSystem.service.captcha.strategy;

import java.util.Set;

public interface CaptchaChallengeStrategy {

    Set<CaptchaStrategyKey> keys();

    boolean verify(CaptchaVerifyRequest request);

    default CaptchaGenerateResult generate(CaptchaGenerateRequest request) {
        throw new UnsupportedOperationException("Captcha generation is not supported by this strategy.");
    }

    default String siteKey() {
        return null;
    }
}
