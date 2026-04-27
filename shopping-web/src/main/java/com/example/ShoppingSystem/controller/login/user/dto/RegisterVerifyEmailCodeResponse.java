package com.example.ShoppingSystem.controller.login.user.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Verify register email code response.
 */
@Data
@Builder
public class RegisterVerifyEmailCodeResponse {

    private boolean success;
    private String message;
    private Long userId;
    private boolean requirePhoneBinding;
}
