package com.example.ShoppingSystem.controller.auth.dto;

public record PreAuthPhoneValidationResponse(boolean success,
                                             String message,
                                             String reasonCode,
                                             String phoneType,
                                             String normalizedE164) {
}
