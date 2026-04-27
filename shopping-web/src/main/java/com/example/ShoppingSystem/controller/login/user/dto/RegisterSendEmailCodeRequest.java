package com.example.ShoppingSystem.controller.login.user.dto;

import lombok.Data;

/**
 * 注册前置验证与邮箱验证码发送请求。
 */
@Data
public class RegisterSendEmailCodeRequest {

    private String email;
    private String username;
    /**
     * 前端使用临时公钥加密后的密码密文（Base64URL）。
     */
    private String passwordCipher;
    /**
     * 临时公钥编号，用于后端定位对应私钥。
     */
    private String kid;
    /**
     * 一次性随机串，配合 Redis 防重放。
     */
    private String nonce;
    /**
     * 前端请求时间戳（毫秒）。
     */
    private Long timestamp;
    private String deviceFingerprint;
    private String captchaUuid;
    private String captchaCode;
}
