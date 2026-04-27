package com.example.ShoppingSystem.controller.login.user.dto;

import lombok.Data;

/**
 * Verify register email code and complete register request.
 */
@Data
public class RegisterVerifyEmailCodeRequest {

    private String email;
    private String emailCode;
    private String deviceFingerprint;
}
