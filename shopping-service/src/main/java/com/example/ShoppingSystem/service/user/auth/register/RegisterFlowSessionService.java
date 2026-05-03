package com.example.ShoppingSystem.service.user.auth.register;

import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowSession;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowStep;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowValidationResult;

/**
 * Authoritative register step flow session service.
 */
public interface RegisterFlowSessionService {

    RegisterFlowSession startFlow(String email,
                                  String deviceFingerprint,
                                  String preAuthToken);

    RegisterFlowValidationResult validate(String flowId, String preAuthToken);

    RegisterFlowValidationResult validate(String flowId,
                                          String preAuthToken,
                                          String deviceFingerprint);

    RegisterFlowSession readFlow(String flowId);

    RegisterFlowSession updateStep(String flowId,
                                   RegisterFlowStep step,
                                   String riskLevel,
                                   boolean requirePhoneBinding,
                                   boolean completed);
}
