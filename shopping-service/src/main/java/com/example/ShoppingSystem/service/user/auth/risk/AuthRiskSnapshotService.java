package com.example.ShoppingSystem.service.user.auth.risk;

import com.example.ShoppingSystem.service.user.auth.register.impl.ChallengePolicy;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationScoreService;
import org.springframework.stereotype.Service;

@Service
public class AuthRiskSnapshotService {

    private static final int DEFAULT_DEVICE_SCORE = 6666;

    private final IpReputationScoreService ipReputationScoreService;
    private final ChallengePolicy challengePolicy;

    public AuthRiskSnapshotService(IpReputationScoreService ipReputationScoreService,
                                   ChallengePolicy challengePolicy) {
        this.ipReputationScoreService = ipReputationScoreService;
        this.challengePolicy = challengePolicy;
    }

    public AuthRiskSnapshot buildRiskSnapshot(String publicIp, String deviceFingerprint) {
        int ipScore = ipReputationScoreService.calculateIpScore(publicIp);
        int deviceScore = resolveDeviceScore(deviceFingerprint);

        int lowScore = Math.min(ipScore, deviceScore);
        int highScore = Math.max(ipScore, deviceScore);
        int totalScore = (int) Math.round(lowScore * 0.8 + highScore * 0.2);

        return new AuthRiskSnapshot(
                ipScore,
                deviceScore,
                totalScore,
                challengePolicy.resolveRiskLevel(totalScore)
        );
    }

    private int resolveDeviceScore(String deviceFingerprint) {
        return DEFAULT_DEVICE_SCORE;
    }
}
