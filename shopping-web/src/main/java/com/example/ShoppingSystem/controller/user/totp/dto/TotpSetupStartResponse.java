package com.example.ShoppingSystem.controller.user.totp.dto;

import com.example.ShoppingSystem.service.user.auth.totp.model.TotpSetupStartResult;

public record TotpSetupStartResponse(
        boolean success,
        String message,
        String secret,
        String otpauthUri,
        int secretBits,
        int digits,
        int periodSeconds
) {

    public static TotpSetupStartResponse from(TotpSetupStartResult result) {
        return new TotpSetupStartResponse(
                result.isSuccess(),
                result.getMessage(),
                result.getSecret(),
                result.getOtpauthUri(),
                result.getSecretBits(),
                result.getDigits(),
                result.getPeriodSeconds()
        );
    }
}
