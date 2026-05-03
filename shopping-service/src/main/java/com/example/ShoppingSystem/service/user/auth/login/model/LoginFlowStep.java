package com.example.ShoppingSystem.service.user.auth.login.model;

public enum LoginFlowStep {
    PASSWORD,
    EMAIL_VERIFICATION,
    TOTP_VERIFICATION,
    ADD_PHONE,
    DONE,
    BLOCKED,
    OPERATION_TIMEOUT;

    public static LoginFlowStep fromStorage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LoginFlowStep.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
