package com.example.ShoppingSystem.controller.login.user.dto;

import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetCryptoKey;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetResult;

public record PasswordResetResponse(boolean success,
                                    String message,
                                    String error,
                                    String riskLevel,
                                    String challengeType,
                                    String verifyUrl,
                                    String redirectPath,
                                    long retryAfterMs,
                                    PasswordResetCryptoKey passwordCrypto) {

    public static PasswordResetResponse from(PasswordResetResult result) {
        return new PasswordResetResponse(
                result.success(),
                result.message(),
                result.error(),
                result.riskLevel(),
                result.challengeType(),
                result.verifyUrl(),
                result.redirectPath(),
                result.retryAfterMs(),
                result.passwordCrypto());
    }
}
