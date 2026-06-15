package com.example.ShoppingSystem.service.captcha.tianai.strategy;

import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaEngine;
import org.springframework.stereotype.Service;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_ROTATE;

@Service
public class TianaiRotateCaptchaStrategy extends AbstractTianaiCaptchaStrategy {

    public TianaiRotateCaptchaStrategy(TianaiCaptchaEngine tianaiCaptchaEngine) {
        super(tianaiCaptchaEngine);
    }

    @Override
    protected String subType() {
        return SUBTYPE_TIANAI_ROTATE;
    }
}
