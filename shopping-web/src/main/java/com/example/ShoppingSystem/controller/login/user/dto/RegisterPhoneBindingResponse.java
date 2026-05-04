package com.example.ShoppingSystem.controller.login.user.dto;

import com.example.ShoppingSystem.service.user.auth.register.model.RegisterPhoneBindingResult;

public record RegisterPhoneBindingResponse(boolean success,
                                           String error,
                                           String reasonCode,
                                           String message,
                                           String phoneType,
                                           String normalizedE164,
                                           Long userId,
                                           String email,
                                           String riskLevel,
                                           String step,
                                           String redirectPath,
                                           Boolean requirePhoneBinding,
                                           Boolean authenticated,
                                           String challengeType,
                                           String challengeSubType,
                                           String challengeSiteKey) {

    public static RegisterPhoneBindingResponse from(RegisterPhoneBindingResult result) {
        if (result == null) {
            return failed("Register phone binding request failed.");
        }
        return new RegisterPhoneBindingResponse(
                result.isSuccess(),
                result.getError(),
                result.getReasonCode(),
                result.getMessage(),
                result.getPhoneType(),
                result.getNormalizedE164(),
                result.getUserId(),
                result.getEmail(),
                result.getRiskLevel(),
                result.getStep(),
                result.getRedirectPath(),
                result.isRequirePhoneBinding(),
                result.isAuthenticated(),
                result.getChallengeType(),
                result.getChallengeSubType(),
                result.getChallengeSiteKey()
        );
    }

    public static RegisterPhoneBindingResponse failed(String message) {
        return new RegisterPhoneBindingResponse(
                false,
                null,
                null,
                message,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                null,
                null,
                null
        );
    }
}
