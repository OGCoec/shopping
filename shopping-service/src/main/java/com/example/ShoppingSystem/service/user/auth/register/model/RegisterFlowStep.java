package com.example.ShoppingSystem.service.user.auth.register.model;

/**
 * Authoritative register flow steps after the entry email page.
 */
public enum RegisterFlowStep {
    PASSWORD,
    EMAIL_VERIFICATION,
    ADD_PHONE,
    DONE;

    public static RegisterFlowStep fromStorage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RegisterFlowStep.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
