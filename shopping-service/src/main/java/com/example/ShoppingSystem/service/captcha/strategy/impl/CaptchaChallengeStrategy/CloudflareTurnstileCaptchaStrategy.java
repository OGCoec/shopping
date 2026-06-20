package com.example.ShoppingSystem.service.captcha.strategy.impl.CaptchaChallengeStrategy;

import com.example.ShoppingSystem.service.captcha.strategy.CaptchaChallengeStrategy;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaStrategyKey;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaVerifyRequest;
import com.example.ShoppingSystem.service.captcha.thirdparty.ThirdPartyCaptchaVerifyClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_CLOUDFLARE_TURNSTILE;

@Service
public class CloudflareTurnstileCaptchaStrategy implements CaptchaChallengeStrategy {

    private final ThirdPartyCaptchaVerifyClient verifyClient;
    private final String siteKey;
    private final String secretKey;
    private final String verifyUrl;

    public CloudflareTurnstileCaptchaStrategy(
            ThirdPartyCaptchaVerifyClient verifyClient,
            @Value("${captcha.turnstile.site-key:}") String siteKey,
            @Value("${captcha.turnstile.secret-key:}") String secretKey,
            @Value("${captcha.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}") String verifyUrl) {
        this.verifyClient = verifyClient;
        this.siteKey = siteKey;
        this.secretKey = secretKey;
        this.verifyUrl = verifyUrl;
    }

    @Override
    public Set<CaptchaStrategyKey> keys() {
        return Set.of(CaptchaStrategyKey.of(CHALLENGE_CLOUDFLARE_TURNSTILE, null));
    }

    @Override
    public boolean verify(CaptchaVerifyRequest request) {
        return verifyClient.validate(
                "Cloudflare Turnstile",
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
