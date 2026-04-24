package com.example.ShoppingSystem.config;

import cloud.tianai.captcha.interceptor.CaptchaInterceptor;
import cloud.tianai.captcha.validator.ImageCaptchaValidator;
import cloud.tianai.captcha.validator.impl.SimpleImageCaptchaValidator;
import com.example.ShoppingSystem.service.captcha.tianai.interceptor.SelectiveTrackCaptchaInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tianai 验证码显式配置。
 * <p>
 * starter 默认也会提供这两个 Bean，但这里显式声明的目的是让项目自己的校验策略可见：
 * 1. 基础位置校验仍然使用 Tianai 默认的 {@link SimpleImageCaptchaValidator}
 * 2. 额外挂上轨迹“像不像机器人”的拦截器，但只对真正的拖动题型启用
 */
@Configuration
public class TianaiCaptchaConfig {

    @Bean
    public ImageCaptchaValidator imageCaptchaValidator() {
        // 显式保留 Tianai 默认的基础位置校验器。
        // 它负责判断用户最终位移/点击位置是否落在容错范围内。
        return new SimpleImageCaptchaValidator();
    }

    @Bean
    public CaptchaInterceptor captchaInterceptor() {
        // 在基础位置校验之外，再补一层“轨迹像不像人”的行为校验。
        // 这里不直接使用 starter 默认的 EmptyCaptchaInterceptor，而是替换成项目自定义拦截器。
        return new SelectiveTrackCaptchaInterceptor();
    }
}
