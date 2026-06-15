package com.example.ShoppingSystem.service.captcha.tianai.strategy;

import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaEngine;
import org.springframework.stereotype.Service;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_CONCAT;

@Service
public class TianaiConcatCaptchaStrategy extends AbstractTianaiCaptchaStrategy {

    public TianaiConcatCaptchaStrategy(TianaiCaptchaEngine tianaiCaptchaEngine) {
        super(tianaiCaptchaEngine);
    }

    @Override
    protected String subType() {
        return SUBTYPE_TIANAI_CONCAT;
    }
}
