package com.example.ShoppingSystem.listener;

import com.example.ShoppingSystem.service.captcha.strategy.CaptchaGenerateRequest;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaStrategyRegistry;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaResourceInitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_TIANAI;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_ROTATE;

@Slf4j
@Component
public class TianaiCaptchaWarmUpListener implements ApplicationListener<ApplicationReadyEvent> {

    private final TianaiCaptchaResourceInitService resourceInitService;
    private final CaptchaStrategyRegistry captchaStrategyRegistry;

    public TianaiCaptchaWarmUpListener(TianaiCaptchaResourceInitService resourceInitService,
                                       CaptchaStrategyRegistry captchaStrategyRegistry) {
        this.resourceInitService = resourceInitService;
        this.captchaStrategyRegistry = captchaStrategyRegistry;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        long start = System.currentTimeMillis();
        try {
            resourceInitService.initializeRotateResources();
            captchaStrategyRegistry.generate(new CaptchaGenerateRequest(
                    CHALLENGE_TIANAI,
                    SUBTYPE_TIANAI_ROTATE,
                    "warmup",
                    null
            ));
            log.info("Tianai Rotate captcha warm-up completed, elapsed={}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("Tianai Rotate captcha warm-up failed, application startup continues.", e);
        }
    }
}
