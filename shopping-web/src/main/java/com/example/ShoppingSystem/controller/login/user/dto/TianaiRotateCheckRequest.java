package com.example.ShoppingSystem.controller.login.user.dto;

import lombok.Data;

/**
 * 天爱 Rotate 验证码校验请求。
 */
@Data
public class TianaiRotateCheckRequest {

    private String captchaId;
    private Float angle;
}
