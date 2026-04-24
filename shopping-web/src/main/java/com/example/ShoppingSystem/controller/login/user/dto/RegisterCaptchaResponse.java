package com.example.ShoppingSystem.controller.login.user.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 注册图形验证码响应。
 */
@Data
@Builder
public class RegisterCaptchaResponse {

    private String uuid;
    private String image;
}
