package com.example.ShoppingSystem.controller.login.user.dto;

import com.example.ShoppingSystem.service.user.auth.login.model.LoginFactor;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowSession;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowStartResult;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowStep;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginVerificationResult;

import java.util.LinkedHashSet;
import java.util.Set;

public record LoginFlowResponse(boolean success,
                                String error,
                                String message,
                                String flowId,
                                Long userId,
                                String email,
                                String riskLevel,
                                String step,
                                String redirectPath,
                                Set<String> availableFactors,
                                Set<String> completedFactors,
                                Integer requiredFactorCount,
                                Boolean requirePhoneBinding,
                                Boolean authenticated,
                                String challengeType,
                                String challengeSubType,
                                String challengeSiteKey,
                                Long retryAfterMs,
                                Long waitUntilEpochMs,
                                String verifyUrl) {

    public static LoginFlowResponse fromStart(LoginFlowStartResult result) {
        if (result == null) {
            return failed("Login request failed.");
        }
        return new LoginFlowResponse(
                result.isSuccess(),
                null,
                result.getMessage(),
                result.getFlowId(),
                null,
                result.getEmail(),
                result.getRiskLevel(),
                stepName(result.getStep()),
                result.getRedirectPath(),
                factorNames(result.getAvailableFactors()),
                factorNames(result.getCompletedFactors()),
                result.getRequiredFactorCount(),
                result.isRequirePhoneBinding(),
                false,
                result.getChallengeType(),
                result.getChallengeSubType(),
                result.getChallengeSiteKey(),
                result.getRetryAfterMs(),
                result.getWaitUntilEpochMs(),
                result.getVerifyUrl()
        );
    }

    public static LoginFlowResponse fromVerify(LoginVerificationResult result) {
        if (result == null) {
            return failed("Login request failed.");
        }
        return new LoginFlowResponse(
                result.isSuccess(),
                result.getError(),
                result.getMessage(),
                result.getFlowId(),
                result.getUserId(),
                result.getEmail(),
                result.getRiskLevel(),
                stepName(result.getStep()),
                result.getRedirectPath(),
                factorNames(result.getAvailableFactors()),
                factorNames(result.getCompletedFactors()),
                result.getRequiredFactorCount(),
                result.isRequirePhoneBinding(),
                result.isAuthenticated(),
                result.getChallengeType(),
                result.getChallengeSubType(),
                result.getChallengeSiteKey(),
                null,
                null,
                null
        );
    }

    public static LoginFlowResponse fromSession(LoginFlowSession session) {
        if (session == null) {
          return failed("Login session expired.");
        }
        return new LoginFlowResponse(
                true,
                null,
                "ok",
                session.getFlowId(),
                session.getUserId(),
                session.getEmail(),
                session.getRiskLevel(),
                stepName(session.getStep()),
                stepPath(session.getStep()),
                factorNames(session.getAvailableFactors()),
                factorNames(session.getCompletedFactors()),
                session.getRequiredFactorCount(),
                session.isRequirePhoneBinding(),
                session.isCompleted(),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static LoginFlowResponse invalidSession(String message, String redirectPath) {
        return new LoginFlowResponse(
                false,
                null,
                message,
                null,
                null,
                null,
                null,
                null,
                redirectPath,
                Set.of(),
                Set.of(),
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static LoginFlowResponse failed(String message) {
        return invalidSession(message, null);
    }

    private static Set<String> factorNames(Set<LoginFactor> factors) {
        if (factors == null || factors.isEmpty()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (LoginFactor factor : factors) {
            if (factor != null) {
                names.add(factor.name());
            }
        }
        return names;
    }

    private static String stepName(LoginFlowStep step) {
        return step == null ? null : step.name();
    }

    private static String stepPath(LoginFlowStep step) {
        if (step == null) {
            return "/shopping/user/log-in";
        }
        return switch (step) {
            case PASSWORD -> "/shopping/user/log-in/password";
            case EMAIL_VERIFICATION -> "/shopping/user/email-verification";
            case TOTP_VERIFICATION -> "/shopping/user/totp-verification";
            case ADD_PHONE -> "/shopping/user/add-phone";
            case DONE -> "/";
            case BLOCKED, OPERATION_TIMEOUT -> "/shopping/user/log-in";
        };
    }
}
