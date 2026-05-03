package com.example.ShoppingSystem.service.user.auth.totp.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TotpVerificationResult {

    boolean success;
    String message;
    Long matchedTimeStep;
}
