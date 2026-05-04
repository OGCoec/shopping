package com.example.ShoppingSystem.service.user.auth.passwordreset.model;

public record PasswordResetResult(boolean success,
                                  String message,
                                  String error,
                                  String riskLevel,
                                  String challengeType,
                                  String verifyUrl,
                                  String redirectPath,
                                  long retryAfterMs,
                                  PasswordResetCryptoKey passwordCrypto) {

    public static PasswordResetResult ok(String message) {
        return new PasswordResetResult(true, message, "", "", "", "", "", 0L, null);
    }

    public static PasswordResetResult okWithRetryAfter(String message, long retryAfterMs) {
        return new PasswordResetResult(true, message, "", "", "", "", "", retryAfterMs, null);
    }

    public static PasswordResetResult verified(String message, String redirectPath) {
        return new PasswordResetResult(true, message, "", "", "", "", redirectPath, 0L, null);
    }

    public static PasswordResetResult cryptoKey(PasswordResetCryptoKey key) {
        return new PasswordResetResult(true, "ok", "", "", "", "", "", 0L, key);
    }

    public static PasswordResetResult fail(String error, String message) {
        return new PasswordResetResult(false, message, error, "", "", "", "", 0L, null);
    }

    public static PasswordResetResult rateLimited(long retryAfterMs) {
        return new PasswordResetResult(
                false,
                "Please wait before requesting another password reset email.",
                "PASSWORD_RESET_RATE_LIMITED",
                "",
                "",
                "",
                "",
                retryAfterMs,
                null);
    }

    public static PasswordResetResult blocked(String riskLevel, String message) {
        return new PasswordResetResult(false, message, "PASSWORD_RESET_BLOCKED", riskLevel, "", "", "", 0L, null);
    }

    public static PasswordResetResult wafRequired(String riskLevel, String verifyUrl) {
        return new PasswordResetResult(
                false,
                "WAF verification is required.",
                "WAF_REQUIRED",
                riskLevel,
                "WAF_REQUIRED",
                verifyUrl,
                "",
                0L,
                null);
    }
}
