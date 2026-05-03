package com.example.ShoppingSystem.controller.login.user.dto;

public record LoginPhoneBindRequest(String dialCode,
                                    String phoneNumber,
                                    String smsCode,
                                    String captchaUuid,
                                    String captchaCode) {
}
