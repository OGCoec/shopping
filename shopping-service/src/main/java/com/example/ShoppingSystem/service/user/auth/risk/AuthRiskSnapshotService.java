package com.example.ShoppingSystem.service.user.auth.risk;

import com.example.ShoppingSystem.service.user.auth.register.impl.ChallengePolicy;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationScoreService;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpL6CountingBloomDecisionService;

public interface AuthRiskSnapshotService {
    public AuthRiskSnapshot buildRiskSnapshot(String publicIp, String deviceFingerprint);
}
