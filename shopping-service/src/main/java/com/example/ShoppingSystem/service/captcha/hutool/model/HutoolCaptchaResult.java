package com.example.ShoppingSystem.service.captcha.hutool.model;

import lombok.Builder;
import lombok.Data;

/**
 * Hutool 图形验证码结果。
 */
@Data
@Builder
public class HutoolCaptchaResult {

    private String uuid;
    private String image;
}
