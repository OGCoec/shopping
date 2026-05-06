package com.example.ShoppingSystem.filter.preauth.domain;

import com.example.ShoppingSystem.filter.preauth.model.PreAuthRiskProfile;
import com.example.ShoppingSystem.quota.DeviceRiskMultiLevelQueryService;
import com.example.ShoppingSystem.quota.IpReputationMultiLevelQueryService;
import com.example.ShoppingSystem.service.user.auth.register.impl.ChallengePolicy;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Risk calculation service for the pre-auth flow.
 */
@Component
public class PreAuthRiskService {

    private static final int DEFAULT_IP_SCORE_WHEN_UNAVAILABLE = 4500;
    private static final int DEFAULT_DEVICE_SCORE = 6666;

    private final IpReputationMultiLevelQueryService ipReputationMultiLevelQueryService;
    private final DeviceRiskMultiLevelQueryService deviceRiskMultiLevelQueryService;
    private final ChallengePolicy challengePolicy;

    public PreAuthRiskService(IpReputationMultiLevelQueryService ipReputationMultiLevelQueryService,
                              DeviceRiskMultiLevelQueryService deviceRiskMultiLevelQueryService,
                              ChallengePolicy challengePolicy) {
        this.ipReputationMultiLevelQueryService = ipReputationMultiLevelQueryService;
        this.deviceRiskMultiLevelQueryService = deviceRiskMultiLevelQueryService;
        this.challengePolicy = challengePolicy;
    }

    public PreAuthRiskProfile resolveRiskProfile(String ip, String deviceFingerprint) {
        int ipScore = resolveIpScore(ip);
        int deviceScore = deviceRiskMultiLevelQueryService.resolveDeviceScore(deviceFingerprint, ip);
        return buildCombinedRiskProfile(ipScore, deviceScore);
    }

    public PreAuthRiskProfile resolveRiskProfile(String ip, int deviceScore) {
        int ipScore = resolveIpScore(ip);
        return buildCombinedRiskProfile(ipScore, deviceScore);
    }

    public boolean isBlockedRisk(String riskLevel) {
        return "L6".equalsIgnoreCase(riskLevel);
    }

    public boolean isChallengeRequired(String riskLevel) {
        if (riskLevel == null) {
            return false;
        }
        String normalized = riskLevel.trim().toUpperCase(Locale.ROOT);
        return "L3".equals(normalized) || "L4".equals(normalized) || "L5".equals(normalized);
    }

    private int resolveIpScore(String ip) {
        int ipScore = DEFAULT_IP_SCORE_WHEN_UNAVAILABLE;
        IpReputationMultiLevelQueryService.MultiLevelQueryResult result =
                ipReputationMultiLevelQueryService.queryEvidence(ip);
        if (result != null && result.success() && result.currentScore() != null) {
            ipScore = result.currentScore();
        }
        return ipScore;
    }

    private PreAuthRiskProfile buildCombinedRiskProfile(int ipScore, int deviceScore) {
        int safeDeviceScore = deviceScore >= 0 ? deviceScore : DEFAULT_DEVICE_SCORE;
        int lowScore = Math.min(ipScore, safeDeviceScore);
        int highScore = Math.max(ipScore, safeDeviceScore);
        int totalScore = (int) Math.round(lowScore * 0.8 + highScore * 0.2);
        return new PreAuthRiskProfile(
                ipScore,
                safeDeviceScore,
                totalScore,
                challengePolicy.resolveRiskLevel(totalScore)
        );
    }
}
