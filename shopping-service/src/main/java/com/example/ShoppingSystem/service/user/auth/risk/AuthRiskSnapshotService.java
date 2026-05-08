package com.example.ShoppingSystem.service.user.auth.risk;

import com.example.ShoppingSystem.service.user.auth.register.impl.ChallengePolicy;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationScoreService;
import com.example.ShoppingSystem.service.user.auth.register.risk.impl.IpL6CountingBloomDecisionService;
import org.springframework.stereotype.Service;

@Service
public class AuthRiskSnapshotService {

    private static final int DEFAULT_DEVICE_SCORE = 7000;
    private static final String RISK_LEVEL_L6 = "L6";

    private final IpReputationScoreService ipReputationScoreService;
    private final IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService;
    private final DeviceL6CountingBloomDecisionService deviceL6CountingBloomDecisionService;
    private final ChallengePolicy challengePolicy;

    public AuthRiskSnapshotService(IpReputationScoreService ipReputationScoreService,
                                   IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService,
                                   DeviceL6CountingBloomDecisionService deviceL6CountingBloomDecisionService,
                                   ChallengePolicy challengePolicy) {
        this.ipReputationScoreService = ipReputationScoreService;
        this.ipL6CountingBloomDecisionService = ipL6CountingBloomDecisionService;
        this.deviceL6CountingBloomDecisionService = deviceL6CountingBloomDecisionService;
        this.challengePolicy = challengePolicy;
    }

    public AuthRiskSnapshot buildRiskSnapshot(String publicIp, String deviceFingerprint) {
        Integer rawIpL6Score = ipL6CountingBloomDecisionService.resolveFastL6ScoreIfHit(publicIp);
        Integer rawDeviceL6Score = deviceL6CountingBloomDecisionService.resolveFastL6ScoreIfHit(deviceFingerprint);
        int ipScore = rawIpL6Score != null ? rawIpL6Score : ipReputationScoreService.calculateIpScore(publicIp);
        int deviceScore = rawDeviceL6Score != null ? rawDeviceL6Score : resolveDeviceScore(deviceFingerprint);
        boolean rawL6BloomBlocked = rawIpL6Score != null || rawDeviceL6Score != null;

        int lowScore = Math.min(ipScore, deviceScore);
        int highScore = Math.max(ipScore, deviceScore);
        int totalScore = rawL6BloomBlocked
                ? lowScore
                : (int) Math.round(lowScore * 0.8 + highScore * 0.2);

        return new AuthRiskSnapshot(
                ipScore,
                deviceScore,
                totalScore,
                rawL6BloomBlocked ? RISK_LEVEL_L6 : challengePolicy.resolveRiskLevel(totalScore)
        );
    }

    private int resolveDeviceScore(String deviceFingerprint) {
        return DEFAULT_DEVICE_SCORE;
    }
}
