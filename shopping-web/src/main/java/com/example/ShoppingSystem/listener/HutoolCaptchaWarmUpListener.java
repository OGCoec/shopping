package com.example.ShoppingSystem.listener;

import com.example.ShoppingSystem.service.captcha.strategy.CaptchaGenerateRequest;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaStrategyRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HUTOOL_SHEAR;

@Slf4j
@Component
public class HutoolCaptchaWarmUpListener implements ApplicationListener<ApplicationReadyEvent> {

    private final CaptchaStrategyRegistry captchaStrategyRegistry;

    public HutoolCaptchaWarmUpListener(CaptchaStrategyRegistry captchaStrategyRegistry) {
        this.captchaStrategyRegistry = captchaStrategyRegistry;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        long start = System.currentTimeMillis();
        try {
            captchaStrategyRegistry.generate(new CaptchaGenerateRequest(
                    CHALLENGE_HUTOOL_SHEAR,
                    null,
                    "warmup",
                    null
            ));
            log.info("Hutool captcha warm-up completed, elapsed={}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("Hutool captcha warm-up failed, application startup continues.", e);
        }
    }
}
