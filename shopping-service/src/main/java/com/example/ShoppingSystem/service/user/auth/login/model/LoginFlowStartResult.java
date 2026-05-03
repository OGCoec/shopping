package com.example.ShoppingSystem.service.user.auth.login.model;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class LoginFlowStartResult {

    boolean success;
    String message;
    String flowId;
    String email;
    String riskLevel;
    LoginFlowStep step;
    String redirectPath;
    Set<LoginFactor> availableFactors;
    Set<LoginFactor> completedFactors;
    int requiredFactorCount;
    boolean requirePhoneBinding;
    String challengeType;
    String challengeSubType;
    String challengeSiteKey;
    Long retryAfterMs;
    Long waitUntilEpochMs;
    String verifyUrl;
}
