package com.example.ShoppingSystem.service.captcha.hutool;

import com.example.ShoppingSystem.service.captcha.hutool.model.HutoolCaptchaResult;

import java.io.IOException;

/**
 * Hutool 图形验证码服务接口。
 */
public interface HutoolCaptchaService {

    HutoolCaptchaResult generateCaptcha(String type, String existingUuid) throws IOException;

    boolean validateCaptcha(String type, String captchaUuid, String captchaCode);
}
