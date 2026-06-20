package com.example.ShoppingSystem.service.captcha.thirdparty.impl.ThirdPartyCaptchaService;

import com.example.ShoppingSystem.service.captcha.thirdparty.ThirdPartyCaptchaService;
import com.example.ShoppingSystem.service.captcha.thirdparty.ThirdPartyCaptchaVerifyClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ThirdPartyCaptchaServiceImpl implements ThirdPartyCaptchaService {

    private final ThirdPartyCaptchaVerifyClient verifyClient;
    private final String turnstileSiteKey;
    private final String turnstileSecretKey;
    private final String turnstileVerifyUrl;
    private final String hCaptchaSiteKey;
    private final String hCaptchaSecretKey;
    private final String hCaptchaVerifyUrl;
    private final String recaptchaSiteKey;
    private final String recaptchaSecretKey;
    private final String recaptchaVerifyUrl;

    public ThirdPartyCaptchaServiceImpl(
            ThirdPartyCaptchaVerifyClient verifyClient,
            @Value("${captcha.turnstile.site-key:}") String turnstileSiteKey,
            @Value("${captcha.turnstile.secret-key:}") String turnstileSecretKey,
            @Value("${captcha.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}") String turnstileVerifyUrl,
            @Value("${captcha.hcaptcha.site-key:}") String hCaptchaSiteKey,
            @Value("${captcha.hcaptcha.secret-key:}") String hCaptchaSecretKey,
            @Value("${captcha.hcaptcha.verify-url:https://api.hcaptcha.com/siteverify}") String hCaptchaVerifyUrl,
            @Value("${captcha.recaptcha.site-key:}") String recaptchaSiteKey,
            @Value("${captcha.recaptcha.secret-key:}") String recaptchaSecretKey,
            @Value("${captcha.recaptcha.verify-url:https://www.google.com/recaptcha/api/siteverify}") String recaptchaVerifyUrl) {
        this.verifyClient = verifyClient;
        this.turnstileSiteKey = turnstileSiteKey;
        this.turnstileSecretKey = turnstileSecretKey;
        this.turnstileVerifyUrl = turnstileVerifyUrl;
        this.hCaptchaSiteKey = hCaptchaSiteKey;
        this.hCaptchaSecretKey = hCaptchaSecretKey;
        this.hCaptchaVerifyUrl = hCaptchaVerifyUrl;
        this.recaptchaSiteKey = recaptchaSiteKey;
        this.recaptchaSecretKey = recaptchaSecretKey;
        this.recaptchaVerifyUrl = recaptchaVerifyUrl;
    }

    @Override
    public String getTurnstileSiteKey() {
        return turnstileSiteKey;
    }

    @Override
    public String getHCaptchaSiteKey() {
        return hCaptchaSiteKey;
    }

    @Override
    public String getRecaptchaSiteKey() {
        return recaptchaSiteKey;
    }

    @Override
    public boolean validateTurnstile(String token, String remoteIp) {
        return verifyClient.validate(
                "Cloudflare Turnstile",
                turnstileVerifyUrl,
                turnstileSecretKey,
                token,
                remoteIp,
                null,
                false
        );
    }

    @Override
    public boolean validateHCaptcha(String token, String remoteIp) {
        return verifyClient.validate(
                "hCaptcha",
                hCaptchaVerifyUrl,
                hCaptchaSecretKey,
                token,
                remoteIp,
                hCaptchaSiteKey,
                true
        );
    }

    @Override
    public boolean validateRecaptcha(String token, String remoteIp) {
        return verifyClient.validate(
                "Google reCAPTCHA",
                recaptchaVerifyUrl,
                recaptchaSecretKey,
                token,
                remoteIp,
                null,
                false
        );
    }
}
