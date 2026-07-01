package com.example.ShoppingSystem.service.user.auth.register;

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
