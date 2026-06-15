package com.example.ShoppingSystem.service.captcha.thirdparty.strategy;

import com.example.ShoppingSystem.service.captcha.strategy.CaptchaChallengeStrategy;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaStrategyKey;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaVerifyRequest;
import com.example.ShoppingSystem.service.captcha.thirdparty.ThirdPartyCaptchaVerifyClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_GOOGLE_RECAPTCHA_V2;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_GOOGLE_RECAPTCHA_V3_LEGACY;

@Service
public class GoogleRecaptchaCaptchaStrategy implements CaptchaChallengeStrategy {

    private final ThirdPartyCaptchaVerifyClient verifyClient;
    private final String siteKey;
    private final String secretKey;
    private final String verifyUrl;

    public GoogleRecaptchaCaptchaStrategy(
            ThirdPartyCaptchaVerifyClient verifyClient,
            @Value("${captcha.recaptcha.site-key:}") String siteKey,
            @Value("${captcha.recaptcha.secret-key:}") String secretKey,
            @Value("${captcha.recaptcha.verify-url:https://www.google.com/recaptcha/api/siteverify}") String verifyUrl) {
        this.verifyClient = verifyClient;
        this.siteKey = siteKey;
        this.secretKey = secretKey;
        this.verifyUrl = verifyUrl;
    }

    @Override
    public Set<CaptchaStrategyKey> keys() {
        return Set.of(
                CaptchaStrategyKey.of(CHALLENGE_GOOGLE_RECAPTCHA_V2, null),
                CaptchaStrategyKey.of(CHALLENGE_GOOGLE_RECAPTCHA_V3_LEGACY, null)
        );
    }

    @Override
    public boolean verify(CaptchaVerifyRequest request) {
        return verifyClient.validate(
                "Google reCAPTCHA",
                verifyUrl,
                secretKey,
                request.captchaCode(),
                request.remoteIp(),
                null,
                false
        );
    }

    @Override
    public String siteKey() {
        return siteKey;
    }
}
