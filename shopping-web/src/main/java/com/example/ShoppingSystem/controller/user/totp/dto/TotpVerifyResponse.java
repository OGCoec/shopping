package com.example.ShoppingSystem.controller.user.totp.dto;

import com.example.ShoppingSystem.service.user.auth.totp.model.TotpVerificationResult;

public record TotpVerifyResponse(
        boolean success,
        String message,
        Long matchedTimeStep
) {

    public static TotpVerifyResponse from(TotpVerificationResult result) {
        return new TotpVerifyResponse(
                result.isSuccess(),
                result.getMessage(),
                result.getMatchedTimeStep()
        );
    }
}
