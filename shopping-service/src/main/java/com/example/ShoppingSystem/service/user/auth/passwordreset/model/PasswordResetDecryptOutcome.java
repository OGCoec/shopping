package com.example.ShoppingSystem.service.user.auth.passwordreset.model;

public record PasswordResetDecryptOutcome(boolean success,
                                          String payload,
                                          String message) {

    public static PasswordResetDecryptOutcome success(String payload) {
        return new PasswordResetDecryptOutcome(true, payload, "ok");
    }

    public static PasswordResetDecryptOutcome failed(String message) {
        return new PasswordResetDecryptOutcome(false, "", message);
    }
}
