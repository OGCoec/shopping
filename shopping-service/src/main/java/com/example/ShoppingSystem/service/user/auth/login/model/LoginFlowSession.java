package com.example.ShoppingSystem.service.user.auth.login.model;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class LoginFlowSession {

    String flowId;
    String preAuthToken;
    String deviceFingerprint;
    String email;
    Long userId;
    String riskLevel;
    LoginFlowStep step;
    Set<LoginFactor> availableFactors;
    Set<LoginFactor> completedFactors;
    int requiredFactorCount;
    boolean requirePhoneBinding;
    boolean completed;
}
