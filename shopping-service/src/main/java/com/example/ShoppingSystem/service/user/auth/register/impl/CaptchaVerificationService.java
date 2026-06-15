package com.example.ShoppingSystem.service.user.auth.register.impl;

import com.example.ShoppingSystem.service.captcha.strategy.CaptchaStrategyRegistry;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaVerifyRequest;
import org.springframework.stereotype.Service;

/**
 * 娉ㄥ唽楠岃瘉鐮佹牎楠屾湇鍔°€? */
@Service
public class CaptchaVerificationService {

    private static final String REGISTER_CAPTCHA_NAMESPACE = "register";

    private final CaptchaStrategyRegistry captchaStrategyRegistry;

    public CaptchaVerificationService(CaptchaStrategyRegistry captchaStrategyRegistry) {
        this.captchaStrategyRegistry = captchaStrategyRegistry;
    }

    /**
     * 鎸?challengeType 鎵ц鏈嶅姟绔獙璇佺爜鏍￠獙銆?     */
    public boolean verifyRequiredCaptcha(String challengeType,
                                         String challengeSubType,
                                         String publicIp,
                                         String captchaUuid,
                                         String captchaCode) {
        return captchaStrategyRegistry.verify(new CaptchaVerifyRequest(
                challengeType,
                challengeSubType,
                REGISTER_CAPTCHA_NAMESPACE,
                captchaUuid,
                captchaCode,
                publicIp
        ));
    }

    public boolean verifyRequiredCaptcha(String challengeType,
                                         String publicIp,
                                         String captchaUuid,
                                         String captchaCode) {
        return verifyRequiredCaptcha(challengeType, null, publicIp, captchaUuid, captchaCode);
    }

    /**
     * 鏍规嵁鎸戞垬绫诲瀷杩斿洖鍓嶇娓叉煋鎵€闇€ siteKey銆?     */
    public String resolveChallengeSiteKey(String challengeType, String challengeSubType) {
        return captchaStrategyRegistry.siteKey(challengeType, challengeSubType);
    }

    public String resolveChallengeSiteKey(String challengeType) {
        return resolveChallengeSiteKey(challengeType, null);
    }
}
