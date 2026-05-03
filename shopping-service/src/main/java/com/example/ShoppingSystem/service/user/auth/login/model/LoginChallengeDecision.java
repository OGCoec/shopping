package com.example.ShoppingSystem.service.user.auth.login.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LoginChallengeDecision {

    boolean required;
    String challengeType;
    String challengeSubType;
    String challengeSiteKey;
    Long retryAfterMs;
    Long waitUntilEpochMs;
    String verifyUrl;
}
