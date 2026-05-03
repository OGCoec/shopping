package com.example.ShoppingSystem.service.user.auth.register.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RegisterPhoneBindingResult {

    boolean success;
    String error;
    String message;
    Long userId;
    String email;
    String riskLevel;
    String step;
    String redirectPath;
    boolean requirePhoneBinding;
    boolean authenticated;
    String challengeType;
    String challengeSubType;
    String challengeSiteKey;
}
