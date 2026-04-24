package com.example.ShoppingSystem.service.user.auth.register.impl;

import com.example.ShoppingSystem.service.captcha.hutool.HutoolCaptchaService;
import com.example.ShoppingSystem.service.captcha.thirdparty.ThirdPartyCaptchaService;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaService;
import org.springframework.stereotype.Service;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_CLOUDFLARE_TURNSTILE;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HCAPTCHA;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HUTOOL_SHEAR;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_TIANAI;

/**
 * 注册验证码校验服务。
 */
@Service
public class CaptchaVerificationService {

    private final HutoolCaptchaService hutoolCaptchaService;
    private final TianaiCaptchaService tianaiCaptchaService;
    private final ThirdPartyCaptchaService thirdPartyCaptchaService;

    public CaptchaVerificationService(HutoolCaptchaService hutoolCaptchaService,
                                      TianaiCaptchaService tianaiCaptchaService,
                                      ThirdPartyCaptchaService thirdPartyCaptchaService) {
        this.hutoolCaptchaService = hutoolCaptchaService;
        this.tianaiCaptchaService = tianaiCaptchaService;
        this.thirdPartyCaptchaService = thirdPartyCaptchaService;
    }

    /**
     * 按 challengeType 执行服务端验证码校验。
     */
    public boolean verifyRequiredCaptcha(String challengeType,
                                         String publicIp,
                                         String captchaUuid,
                                         String captchaCode) {
        return switch (challengeType) {
            case CHALLENGE_HUTOOL_SHEAR -> hutoolCaptchaService.validateCaptcha("register", captchaUuid, captchaCode);
            case CHALLENGE_TIANAI -> tianaiCaptchaService.validateCaptcha(captchaUuid, captchaCode);
            case CHALLENGE_CLOUDFLARE_TURNSTILE -> thirdPartyCaptchaService.validateTurnstile(captchaCode, publicIp);
            case CHALLENGE_HCAPTCHA -> thirdPartyCaptchaService.validateHCaptcha(captchaCode, publicIp);
            default -> false;
        };
    }

    /**
     * 根据挑战类型返回前端渲染所需 siteKey。
     */
    public String resolveChallengeSiteKey(String challengeType) {
        if (CHALLENGE_CLOUDFLARE_TURNSTILE.equals(challengeType)) {
            return thirdPartyCaptchaService.getTurnstileSiteKey();
        }
        if (CHALLENGE_HCAPTCHA.equals(challengeType)) {
            return thirdPartyCaptchaService.getHCaptchaSiteKey();
        }
        return null;
    }
}
