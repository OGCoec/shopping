package com.example.ShoppingSystem.service.user.auth.register;

import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import com.example.ShoppingSystem.service.user.auth.register.model.RiskSnapshot;
import com.example.ShoppingSystem.service.user.auth.risk.AuthRiskSnapshot;
import com.example.ShoppingSystem.service.user.auth.risk.AuthRiskSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface RiskSnapshotService {
    public RiskSnapshot buildRiskSnapshot(String publicIp,
                                          String deviceFingerprint,
                                          ChallengeSelection pendingChallengeSelection);

    public RiskSnapshot buildRiskSnapshot(String publicIp,
                                          String deviceFingerprint,
                                          ChallengeSelection pendingChallengeSelection,
                                          AuthRiskSnapshot riskSnapshotOverride);
}
