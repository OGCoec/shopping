package com.example.ShoppingSystem.service.captcha.hutool.strategy;

import com.example.ShoppingSystem.service.captcha.hutool.HutoolCaptchaService;
import com.example.ShoppingSystem.service.captcha.hutool.model.HutoolCaptchaResult;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaChallengeStrategy;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaGenerateRequest;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaGenerateResult;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaStrategyKey;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaVerifyRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Set;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HUTOOL_SHEAR;

@Service
public class HutoolShearCaptchaStrategy implements CaptchaChallengeStrategy {

    private final HutoolCaptchaService hutoolCaptchaService;

    public HutoolShearCaptchaStrategy(HutoolCaptchaService hutoolCaptchaService) {
        this.hutoolCaptchaService = hutoolCaptchaService;
    }

    @Override
    public Set<CaptchaStrategyKey> keys() {
        return Set.of(CaptchaStrategyKey.of(CHALLENGE_HUTOOL_SHEAR, null));
    }

    @Override
    public boolean verify(CaptchaVerifyRequest request) {
        return hutoolCaptchaService.validateCaptcha(
                request.captchaNamespace(),
                request.captchaUuid(),
                request.captchaCode()
        );
    }

    @Override
    public CaptchaGenerateResult generate(CaptchaGenerateRequest request) {
        try {
            HutoolCaptchaResult result = hutoolCaptchaService.generateCaptcha(
                    request.captchaNamespace(),
                    request.existingCaptchaIdOrUuid()
            );
            return CaptchaGenerateResult.hutool(result.getUuid(), result.getImage());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Hutool captcha.", e);
        }
    }
}
