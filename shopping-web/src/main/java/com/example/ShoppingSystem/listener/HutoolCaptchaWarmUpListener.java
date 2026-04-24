package com.example.ShoppingSystem.listener;

import com.example.ShoppingSystem.service.captcha.hutool.HutoolCaptchaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Hutool 图形验证码预热监听器。
 * 在应用完全启动后主动生成一次验证码，避免首次真实请求出现明显延迟。
 */
@Slf4j
@Component
public class HutoolCaptchaWarmUpListener implements ApplicationListener<ApplicationReadyEvent> {

    private final HutoolCaptchaService hutoolCaptchaService;

    public HutoolCaptchaWarmUpListener(HutoolCaptchaService hutoolCaptchaService) {
        this.hutoolCaptchaService = hutoolCaptchaService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        long start = System.currentTimeMillis();
        try {
            hutoolCaptchaService.generateCaptcha("warmup", null);
            log.info("Hutool 图形验证码预热完成，耗时 {} ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("Hutool 图形验证码预热失败，不影响应用启动", e);
        }
    }
}
