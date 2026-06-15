package com.example.ShoppingSystem.service.captcha.tianai.strategy;

import com.example.ShoppingSystem.service.captcha.strategy.CaptchaChallengeStrategy;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaGenerateRequest;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaGenerateResult;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaStrategyKey;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaVerifyRequest;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaEngine;

import java.util.Set;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_TIANAI;

abstract class AbstractTianaiCaptchaStrategy implements CaptchaChallengeStrategy {

    private final TianaiCaptchaEngine tianaiCaptchaEngine;

    protected AbstractTianaiCaptchaStrategy(TianaiCaptchaEngine tianaiCaptchaEngine) {
        this.tianaiCaptchaEngine = tianaiCaptchaEngine;
    }

    @Override
    public Set<CaptchaStrategyKey> keys() {
        return Set.of(CaptchaStrategyKey.of(CHALLENGE_TIANAI, subType()));
    }

    @Override
    public boolean verify(CaptchaVerifyRequest request) {
        return tianaiCaptchaEngine.validate(request.captchaUuid(), request.captchaCode());
    }

    @Override
    public CaptchaGenerateResult generate(CaptchaGenerateRequest request) {
        return CaptchaGenerateResult.tianai(
                tianaiCaptchaEngine.generate(subType(), request.existingCaptchaIdOrUuid())
        );
    }

    protected abstract String subType();
}
