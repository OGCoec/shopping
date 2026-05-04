package com.example.ShoppingSystem.controller.login.user.dto;

public record PasswordResetSubmitRequest(String email,
                                         String code,
                                         String token,
                                         String kid,
                                         String payloadCipher,
                                         String nonce,
                                         Long timestamp) {
}
