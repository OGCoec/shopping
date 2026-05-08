package com.example.ShoppingSystem.service.user.auth.phone.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthenticatedPhoneBindingResult {

    boolean success;
    String error;
    String reasonCode;
    String message;
    String phoneType;
    String normalizedE164;
    Long userId;
    String riskLevel;
    String redirectPath;
    boolean requirePhoneBinding;
    boolean authenticated;
    String challengeType;
    String challengeSubType;
    String challengeSiteKey;
    Long retryAfterMs;
}
