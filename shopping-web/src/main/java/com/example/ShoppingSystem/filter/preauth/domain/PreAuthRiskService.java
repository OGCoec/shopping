package com.example.ShoppingSystem.filter.preauth.domain;

import com.example.ShoppingSystem.filter.preauth.model.PreAuthRiskProfile;
import com.example.ShoppingSystem.quota.DeviceRiskMultiLevelQueryService;
import com.example.ShoppingSystem.quota.IpReputationMultiLevelQueryService;
import com.example.ShoppingSystem.service.user.auth.register.impl.ChallengePolicy;
import com.example.ShoppingSystem.service.user.auth.register.risk.impl.IpL6CountingBloomDecisionService;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceL6CountingBloomDecisionService;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Risk calculation service for the pre-auth flow.
 */
@Component
public class PreAuthRiskService {

    private static final int DEFAULT_IP_SCORE_WHEN_UNAVAILABLE = 4500;
    private static final int DEFAULT_DEVICE_SCORE = 7000;
    private static final String RISK_LEVEL_L6 = "L6";

    private final IpReputationMultiLevelQueryService ipReputationMultiLevelQueryService;
    private final DeviceRiskMultiLevelQueryService deviceRiskMultiLevelQueryService;
    private final IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService;
    private final DeviceL6CountingBloomDecisionService deviceL6CountingBloomDecisionService;
    private final ChallengePolicy challengePolicy;

    public PreAuthRiskService(IpReputationMultiLevelQueryService ipReputationMultiLevelQueryService,
                              DeviceRiskMultiLevelQueryService deviceRiskMultiLevelQueryService,
                              IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService,
                              DeviceL6CountingBloomDecisionService deviceL6CountingBloomDecisionService,
                              ChallengePolicy challengePolicy) {
        this.ipReputationMultiLevelQueryService = ipReputationMultiLevelQueryService;
        this.deviceRiskMultiLevelQueryService = deviceRiskMultiLevelQueryService;
        this.ipL6CountingBloomDecisionService = ipL6CountingBloomDecisionService;
        this.deviceL6CountingBloomDecisionService = deviceL6CountingBloomDecisionService;
        this.challengePolicy = challengePolicy;
    }

    public PreAuthRiskProfile resolveRiskProfile(String ip, String deviceFingerprint) {
        Integer rawIpL6Score = resolveIpL6BloomScore(ip);
        Integer rawDeviceL6Score = resolveDeviceL6BloomScore(deviceFingerprint);
        int ipScore = rawIpL6Score != null ? rawIpL6Score : resolveIpScore(ip);
        int deviceScore = rawDeviceL6Score != null
                ? rawDeviceL6Score
                : deviceRiskMultiLevelQueryService.resolveDeviceScore(deviceFingerprint, ip);
        return buildCombinedRiskProfile(ipScore, deviceScore, rawIpL6Score != null || rawDeviceL6Score != null);
    }

    public PreAuthRiskProfile resolveRiskProfile(String ip, int deviceScore) {
        Integer rawIpL6Score = resolveIpL6BloomScore(ip);
        int ipScore = rawIpL6Score != null ? rawIpL6Score : resolveIpScore(ip);
        return buildCombinedRiskProfile(ipScore, deviceScore, rawIpL6Score != null);
    }

    public boolean isRawL6BloomBlocked(String ip, String deviceFingerprint) {
        return resolveIpL6BloomScore(ip) != null || resolveDeviceL6BloomScore(deviceFingerprint) != null;
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

    private Integer resolveIpL6BloomScore(String ip) {
        return ipL6CountingBloomDecisionService.resolveFastL6ScoreIfHit(ip);
    }

    private Integer resolveDeviceL6BloomScore(String deviceFingerprint) {
        return deviceL6CountingBloomDecisionService.resolveFastL6ScoreIfHit(deviceFingerprint);
    }

    private PreAuthRiskProfile buildCombinedRiskProfile(int ipScore, int deviceScore, boolean rawL6BloomBlocked) {
        int safeDeviceScore = deviceScore >= 0 ? deviceScore : DEFAULT_DEVICE_SCORE;
        int lowScore = Math.min(ipScore, safeDeviceScore);
        int highScore = Math.max(ipScore, safeDeviceScore);
        int totalScore = rawL6BloomBlocked
                ? lowScore
                : (int) Math.round(lowScore * 0.8 + highScore * 0.2);
        return new PreAuthRiskProfile(
                ipScore,
                safeDeviceScore,
                totalScore,
                rawL6BloomBlocked ? RISK_LEVEL_L6 : challengePolicy.resolveRiskLevel(totalScore)
        );
    }
}
