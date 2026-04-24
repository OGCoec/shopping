package com.example.ShoppingSystem.controller.login.user.dto;

import lombok.Data;

/**
 * 注册前置验证与邮箱验证码发送请求。
 */
@Data
public class RegisterSendEmailCodeRequest {

    private String email;
    private String username;
    private String password;
    private String deviceFingerprint;
    private String captchaUuid;
    private String captchaCode;
}
