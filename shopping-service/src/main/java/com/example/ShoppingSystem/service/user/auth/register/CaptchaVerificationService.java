package com.example.ShoppingSystem.service.user.auth.register;

import com.example.ShoppingSystem.service.captcha.strategy.CaptchaStrategyRegistry;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaVerifyRequest;

public interface CaptchaVerificationService {
    public boolean verifyRequiredCaptcha(String challengeType,
                                         String challengeSubType,
                                         String publicIp,
                                         String captchaUuid,
                                         String captchaCode);

    public boolean verifyRequiredCaptcha(String challengeType,
                                         String publicIp,
                                         String captchaUuid,
                                         String captchaCode);

    public String resolveChallengeSiteKey(String challengeType, String challengeSubType);

    public String resolveChallengeSiteKey(String challengeType);
}
