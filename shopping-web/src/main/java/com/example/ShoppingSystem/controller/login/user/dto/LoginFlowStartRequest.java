package com.example.ShoppingSystem.controller.login.user.dto;

public record LoginFlowStartRequest(String email,
                                    String deviceFingerprint,
                                    String captchaUuid,
                                    String captchaCode) {
}
