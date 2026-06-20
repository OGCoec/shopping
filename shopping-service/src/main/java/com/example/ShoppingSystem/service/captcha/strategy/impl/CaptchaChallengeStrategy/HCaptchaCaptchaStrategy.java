package com.example.ShoppingSystem.service.captcha.strategy.impl.CaptchaChallengeStrategy;

import com.example.ShoppingSystem.service.captcha.strategy.CaptchaChallengeStrategy;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaStrategyKey;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaVerifyRequest;
import com.example.ShoppingSystem.service.captcha.thirdparty.ThirdPartyCaptchaVerifyClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HCAPTCHA;

@Service
public class HCaptchaCaptchaStrategy implements CaptchaChallengeStrategy {

    private final ThirdPartyCaptchaVerifyClient verifyClient;
    private final String siteKey;
    private final String secretKey;
    private final String verifyUrl;

    public HCaptchaCaptchaStrategy(
            ThirdPartyCaptchaVerifyClient verifyClient,
            @Value("${captcha.hcaptcha.site-key:}") String siteKey,
            @Value("${captcha.hcaptcha.secret-key:}") String secretKey,
            @Value("${captcha.hcaptcha.verify-url:https://api.hcaptcha.com/siteverify}") String verifyUrl) {
        this.verifyClient = verifyClient;
        this.siteKey = siteKey;
        this.secretKey = secretKey;
        this.verifyUrl = verifyUrl;
    }

    @Override
    public Set<CaptchaStrategyKey> keys() {
        return Set.of(CaptchaStrategyKey.of(CHALLENGE_HCAPTCHA, null));
    }

    @Override
    public boolean verify(CaptchaVerifyRequest request) {
        return verifyClient.validate(
                "hCaptcha",
                verifyUrl,
                secretKey,
                request.captchaCode(),
                request.remoteIp(),
                siteKey,
                true
        );
    }

    @Override
    public String siteKey() {
        return siteKey;
    }
}
