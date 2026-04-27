package com.example.ShoppingSystem.controller.auth.dto;

public record PreAuthPhoneValidationRequest(String dialCode,
                                            String phoneNumber) {
}
