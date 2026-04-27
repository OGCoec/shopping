package com.example.ShoppingSystem.filter.preauth.domain;

import com.example.ShoppingSystem.filter.preauth.model.PreAuthRiskProfile;
import com.example.ShoppingSystem.quota.IpReputationMultiLevelQueryService;
import com.example.ShoppingSystem.service.user.auth.register.impl.ChallengePolicy;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * preauth 场景下的风险计算服务。
 * <p>
 * 负责把外部 IP 风险查询结果转换为当前模块能直接使用的风险对象与策略判断。
 */
@Component
public class PreAuthRiskService {

    /** 外部风险服务不可用时的兜底风险分。 */
    private static final int DEFAULT_SCORE_WHEN_UNAVAILABLE = 4500;

    private final IpReputationMultiLevelQueryService ipReputationMultiLevelQueryService;
    private final ChallengePolicy challengePolicy;

    public PreAuthRiskService(IpReputationMultiLevelQueryService ipReputationMultiLevelQueryService,
            ChallengePolicy challengePolicy) {
        this.ipReputationMultiLevelQueryService = ipReputationMultiLevelQueryService;
        this.challengePolicy = challengePolicy;
    }

    /**
     * 根据 IP 解析出当前预登录流程需要的风险分与风险等级。
     */
    public PreAuthRiskProfile resolveRiskProfile(String ip) {
        int score = DEFAULT_SCORE_WHEN_UNAVAILABLE;

        // 先查询外部多级 IP 风险证据。
        IpReputationMultiLevelQueryService.MultiLevelQueryResult result = ipReputationMultiLevelQueryService
                .queryEvidence(ip);
        if (result != null && result.success() && result.currentScore() != null) {
            score = result.currentScore();
        }

        // 再用站内策略把数值分映射成 L3/L4/L5/L6 等等级。
        return new PreAuthRiskProfile(score, challengePolicy.resolveRiskLevel(score));
    }

    /**
     * 判断某个风险等级是否需要直接阻断。
     * <p>
     * 当前策略下，只有 L6 直接封禁。
     */
    public boolean isBlockedRisk(String riskLevel) {
        return "L6".equalsIgnoreCase(riskLevel);
    }

    /**
     * 判断某个风险等级是否需要额外挑战。
     * <p>
     * 当前策略下，L3/L4/L5 需要挑战，L6 直接拦截。
     */
    public boolean isChallengeRequired(String riskLevel) {
        if (riskLevel == null) {
            return false;
        }
        String normalized = riskLevel.trim().toUpperCase(Locale.ROOT);
        return "L3".equals(normalized) || "L4".equals(normalized) || "L5".equals(normalized);
    }
}
