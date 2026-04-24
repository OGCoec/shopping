package com.example.ShoppingSystem.controller.auth.dto;

/**
 * 预登录阶段手机号国家探测响应。
 */
public record PreAuthPhoneCountryResponse(boolean success,
                                          String message,
                                          String country,
                                          String source) {
}
