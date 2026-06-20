package com.example.ShoppingSystem.service.captcha.strategy.impl.CaptchaChallengeStrategy;

import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaEngine;
import org.springframework.stereotype.Service;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_SLIDER;

@Service
public class TianaiSliderCaptchaStrategy extends AbstractTianaiCaptchaStrategy {

    public TianaiSliderCaptchaStrategy(TianaiCaptchaEngine tianaiCaptchaEngine) {
        super(tianaiCaptchaEngine);
    }

    @Override
    protected String subType() {
        return SUBTYPE_TIANAI_SLIDER;
    }
}
