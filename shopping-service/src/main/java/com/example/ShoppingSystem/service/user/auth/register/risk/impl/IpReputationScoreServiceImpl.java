package com.example.ShoppingSystem.service.user.auth.register.risk.impl;

import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationDataProvider;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationEvidence;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * IP 参考分计算服务。
 * <p>
 * 公式来源：ip_reputation_reference_score_formula.txt。
 */
@Service
public class IpReputationScoreServiceImpl implements IpReputationScoreService {

    private static final Logger log = LoggerFactory.getLogger(IpReputationScoreServiceImpl.class);

    private static final int SCORE_MIN = 0;
    private static final int SCORE_MAX = 10000;
    private static final int DEFAULT_SCORE_WHEN_EVIDENCE_UNAVAILABLE = 6000;
    private static final int FRAUD_PENALTY_MULTIPLIER = 80;

    private final IpReputationDataProvider ipReputationDataProvider;
    private final IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService;

    public IpReputationScoreServiceImpl(IpReputationDataProvider ipReputationDataProvider,
                                        IpL6CountingBloomDecisionService ipL6CountingBloomDecisionService) {
        this.ipReputationDataProvider = ipReputationDataProvider;
        this.ipL6CountingBloomDecisionService = ipL6CountingBloomDecisionService;
    }

    @Override
    public int calculateIpScore(String publicIp) {
        if (isBlank(publicIp)) {
            log.info("IP评分：publicIp为空，使用保底分={}", DEFAULT_SCORE_WHEN_EVIDENCE_UNAVAILABLE);
            return DEFAULT_SCORE_WHEN_EVIDENCE_UNAVAILABLE;
        }

        Integer fastL6Score = ipL6CountingBloomDecisionService.resolveFastL6ScoreIfHit(publicIp);
        if (fastL6Score != null) {
            log.info("IP评分快速判定：publicIp={} 命中L6计数布隆，快速分={}", publicIp, fastL6Score);
            return fastL6Score;
        }

        IpReputationEvidence evidence = ipReputationDataProvider.fetchEvidence(publicIp);
        if (evidence == null || !evidence.available()) {
            log.info("IP评分：publicIp={}，证据不可用，使用保底分={}",
                    publicIp,
                    DEFAULT_SCORE_WHEN_EVIDENCE_UNAVAILABLE);
            return DEFAULT_SCORE_WHEN_EVIDENCE_UNAVAILABLE;
        }

        // 本地缓存/Redis/DB 命中时会带预计算分，直接返回即可。
        if (evidence.hasResolvedScore()) {
            int resolvedScore = clamp(evidence.resolvedScore(), SCORE_MIN, SCORE_MAX);
            log.info("IP评分：publicIp={}，命中预计算分，directScore={}", publicIp, resolvedScore);
            return resolvedScore;
        }

        int fraudScore = clamp(evidence.fraudScore(), 0, 100);
        String usageType = normalizeToken(evidence.usageType());
        String proxyType = normalizeToken(evidence.proxyType());
        String asUsageType = normalizeToken(evidence.asUsageType());

        int fraudPenalty = fraudScore * FRAUD_PENALTY_MULTIPLIER;
        int usageTypePenalty = usageTypePenalty(usageType);
        int proxyTypePenalty = proxyTypePenalty(proxyType);
        int flagPenalty = flagPenalty(evidence);
        int asUsagePenalty = asUsagePenalty(asUsageType);
        int trustBonus = trustBonus(evidence, fraudScore, usageType);

        int referenceScore = 10000
                - fraudPenalty
                - usageTypePenalty
                - proxyTypePenalty
                - flagPenalty
                - asUsagePenalty
                + trustBonus;

        int finalScore = clamp(referenceScore, SCORE_MIN, SCORE_MAX);
        log.info(
                "IP评分计算过程：publicIp={}，fraudScore={}，usageType={}，proxyType={}，asUsageType={}，addressType={}，"
                        + "fraudPenalty={}，usageTypePenalty={}，proxyTypePenalty={}，flagPenalty={}，asUsagePenalty={}，trustBonus={}，"
                        + "referenceScore={}，finalScore={}",
                publicIp,
                fraudScore,
                usageType,
                proxyType,
                asUsageType,
                evidence.addressType(),
                fraudPenalty,
                usageTypePenalty,
                proxyTypePenalty,
                flagPenalty,
                asUsagePenalty,
                trustBonus,
                referenceScore,
                finalScore);
        return finalScore;
    }

    private int usageTypePenalty(String usageType) {
        return switch (usageType) {
            case "RESIDENTIAL" -> 0;
            case "MOBILE" -> 170;
            case "BUSINESS" -> 340;
            case "UNKNOWN" -> 670;
            case "DCH" -> 1510;
            default -> 670;
        };
    }

    private int proxyTypePenalty(String proxyType) {
        return switch (proxyType) {
            case "VPN" -> 1260;
            case "WEB" -> 1510;
            case "PUB" -> 1850;
            case "TOR" -> 2690;
            case "DCH" -> 1340;
            case "RES" -> 420;
            case "CPN" -> 670;
            case "EPN" -> 500;
            case "SES" -> 1010;
            case "", "-" -> 0;
            default -> 420;
        };
    }

    private int flagPenalty(IpReputationEvidence evidence) {
        int penalty = 0;
        if (evidence.proxyIsTor()) {
            penalty += 800;
        }
        if (evidence.proxyIsPublicProxy()) {
            penalty += 600;
        }
        if (evidence.proxyIsWebProxy()) {
            penalty += 500;
        }
        if (evidence.proxyIsVpn()) {
            penalty += 500;
        }
        if (evidence.proxyIsDataCenter()) {
            penalty += 400;
        }
        if (evidence.proxyIsResidentialProxy()) {
            penalty += 200;
        }
        if (evidence.proxyIsConsumerPrivacyNetwork()) {
            penalty += 300;
        }
        if (evidence.proxyIsEnterprisePrivateNetwork()) {
            penalty += 200;
        }
        return penalty;
    }

    private int asUsagePenalty(String asUsageType) {
        return switch (asUsageType) {
            case "DCH" -> 600;
            case "MOBILE" -> 100;
            case "BUSINESS" -> 200;
            case "UNKNOWN" -> 300;
            case "", "-" -> 0;
            default -> 0;
        };
    }

    private int trustBonus(IpReputationEvidence evidence, int fraudScore, String usageType) {
        int bonus = 0;
        if ("UNICAST".equals(normalizeToken(evidence.addressType()))) {
            bonus += 100;
        }
        if (fraudScore <= 5) {
            bonus += 150;
        }

        boolean trustedUsageType = "RESIDENTIAL".equals(usageType) || "MOBILE".equals(usageType);
        boolean allProxyFlagsFalse = !evidence.proxyIsVpn()
                && !evidence.proxyIsTor()
                && !evidence.proxyIsDataCenter()
                && !evidence.proxyIsPublicProxy()
                && !evidence.proxyIsWebProxy()
                && !evidence.proxyIsResidentialProxy()
                && !evidence.proxyIsConsumerPrivacyNetwork()
                && !evidence.proxyIsEnterprisePrivateNetwork();
        if (trustedUsageType && allProxyFlagsFalse) {
            bonus += 300;
        }
        return bonus;
    }

    private String normalizeToken(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
