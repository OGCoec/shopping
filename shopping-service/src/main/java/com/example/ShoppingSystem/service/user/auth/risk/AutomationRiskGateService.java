package com.example.ShoppingSystem.service.user.auth.risk;

import com.example.ShoppingSystem.service.user.auth.risk.model.AutomationRiskDecision;
import com.example.ShoppingSystem.service.user.auth.risk.model.AutomationRiskScene;

public interface AutomationRiskGateService {

    AutomationRiskDecision checkStart(AutomationRiskScene scene, String deviceFingerprint, String publicIp);

    AutomationRiskDecision recordUnknownLoginIdentifier(String deviceFingerprint, String publicIp);
}
