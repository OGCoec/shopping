package com.example.ShoppingSystem.listener;

import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaResourceInitService;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 天爱验证码启动预热监听器。
 * 应用启动后先导入默认模板资源，再主动生成一次 Rotate 验证码以预热生成链路。
 */
@Slf4j
@Component
public class TianaiCaptchaWarmUpListener implements ApplicationListener<ApplicationReadyEvent> {

    private final TianaiCaptchaResourceInitService resourceInitService;
    private final TianaiCaptchaService tianaiCaptchaService;

    public TianaiCaptchaWarmUpListener(TianaiCaptchaResourceInitService resourceInitService,
                                       TianaiCaptchaService tianaiCaptchaService) {
        this.resourceInitService = resourceInitService;
        this.tianaiCaptchaService = tianaiCaptchaService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        long start = System.currentTimeMillis();
        try {
            resourceInitService.initializeRotateResources();
            tianaiCaptchaService.generateRotateCaptcha();
            log.info("天爱 Rotate 验证码预热完成，耗时 {} ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("天爱 Rotate 验证码预热失败，不影响应用启动", e);
        }
    }
}
