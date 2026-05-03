package com.example.ShoppingSystem.service.user.auth.register.model;

import lombok.Builder;
import lombok.Data;

/**
 * Server-side register flow session snapshot.
 */
@Data
@Builder
public class RegisterFlowSession {

    private String flowId;
    private String preAuthToken;
    private String deviceFingerprint;
    private String email;
    private RegisterFlowStep step;
    private String riskLevel;
    private boolean requirePhoneBinding;
    private boolean completed;
}
