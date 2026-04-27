package com.example.ShoppingSystem.service.user.auth.register.model;

import lombok.Builder;
import lombok.Data;

/**
 * Register completion result after email code verification.
 */
@Data
@Builder
public class RegisterCompletionResult {

    private boolean success;
    private String message;
    private Long userId;
    private boolean requirePhoneBinding;
}
