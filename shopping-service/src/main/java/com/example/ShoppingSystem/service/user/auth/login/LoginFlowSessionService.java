package com.example.ShoppingSystem.service.user.auth.login;

import com.example.ShoppingSystem.service.user.auth.login.model.LoginFactor;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowSession;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowStep;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowValidationResult;

import java.util.Set;

public interface LoginFlowSessionService {

    LoginFlowSession startFlow(String email,
                               Long userId,
                               String riskLevel,
                               Set<LoginFactor> availableFactors,
                               int requiredFactorCount,
                               boolean requirePhoneBinding,
                               String deviceFingerprint,
                               String preAuthToken);

    LoginFlowSession readFlow(String flowId);

    LoginFlowValidationResult validate(String flowId, String preAuthToken);

    LoginFlowValidationResult validate(String flowId, String preAuthToken, String deviceFingerprint);

    LoginFlowSession updateStep(String flowId,
                                LoginFlowStep step,
                                Set<LoginFactor> completedFactors,
                                boolean requirePhoneBinding,
                                boolean completed);

    LoginFlowSession resolveIdentity(String flowId,
                                     Long userId,
                                     Set<LoginFactor> availableFactors,
                                     int requiredFactorCount,
                                     boolean requirePhoneBinding);
}
