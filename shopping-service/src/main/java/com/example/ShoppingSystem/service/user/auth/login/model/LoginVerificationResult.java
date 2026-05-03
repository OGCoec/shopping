package com.example.ShoppingSystem.service.user.auth.login.model;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class LoginVerificationResult {

    boolean success;
    String error;
    String message;
    String flowId;
    Long userId;
    String email;
    String riskLevel;
    LoginFlowStep step;
    String redirectPath;
    Set<LoginFactor> availableFactors;
    Set<LoginFactor> completedFactors;
    int requiredFactorCount;
    boolean requirePhoneBinding;
    boolean authenticated;
    String challengeType;
    String challengeSubType;
    String challengeSiteKey;
}
