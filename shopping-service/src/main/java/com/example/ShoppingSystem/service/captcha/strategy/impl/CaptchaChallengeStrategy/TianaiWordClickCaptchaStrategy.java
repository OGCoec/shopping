package com.example.ShoppingSystem.service.captcha.strategy.impl.CaptchaChallengeStrategy;

import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaEngine;
import org.springframework.stereotype.Service;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_WORD_IMAGE_CLICK;

@Service
public class TianaiWordClickCaptchaStrategy extends AbstractTianaiCaptchaStrategy {

    public TianaiWordClickCaptchaStrategy(TianaiCaptchaEngine tianaiCaptchaEngine) {
        super(tianaiCaptchaEngine);
    }

    @Override
    protected String subType() {
        return SUBTYPE_TIANAI_WORD_IMAGE_CLICK;
    }
}
