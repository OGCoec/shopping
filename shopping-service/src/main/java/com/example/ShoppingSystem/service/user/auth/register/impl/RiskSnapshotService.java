package com.example.ShoppingSystem.service.user.auth.register.impl;

import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import com.example.ShoppingSystem.service.user.auth.register.model.RiskSnapshot;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 风险快照服务。
 * <p>
 * 职责：
 * 1) 计算 IP 分（通过 {@link IpReputationScoreService}）；
 * 2) 合成设备分与总分；
 * 3) 映射风险等级；
 * 4) 结合 pending challenge 生成最终 challenge 选择；
 * 5) 返回统一的 {@link RiskSnapshot}。
 */
@Service
public class RiskSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(RiskSnapshotService.class);

    /**
     * 当前设备分兜底值。
     * 说明：后续接入设备信誉服务后，这个常量可以替换为实时计算结果。
     */
    private static final int DEFAULT_DEVICE_SCORE = 6666;

    private final IpReputationScoreService ipReputationScoreService;
    private final ChallengePolicy challengePolicy;

    public RiskSnapshotService(IpReputationScoreService ipReputationScoreService,
                               ChallengePolicy challengePolicy) {
        this.ipReputationScoreService = ipReputationScoreService;
        this.challengePolicy = challengePolicy;
    }

    /**
     * 构建本次注册请求的风险快照。
     * <p>
     * 总分计算公式（保留当前策略）：
     * low*0.8 + high*0.2
     * 其中 low = min(ipScore, deviceScore)，high = max(ipScore, deviceScore)。
     *
     * @param publicIp 客户端公网 IP
     * @param pendingChallengeSelection 若存在则优先复用，避免挑战类型漂移
     * @return 风险快照
     */
    public RiskSnapshot buildRiskSnapshot(String publicIp, ChallengeSelection pendingChallengeSelection) {
        int ipScore = ipReputationScoreService.calculateIpScore(publicIp);
        int deviceScore = DEFAULT_DEVICE_SCORE;

        int lowScore = Math.min(ipScore, deviceScore);
        int highScore = Math.max(ipScore, deviceScore);
        int totalScore = (int) Math.round(lowScore * 0.8 + highScore * 0.2);

        String riskLevel = challengePolicy.resolveRiskLevel(totalScore);
        ChallengeSelection challengeSelection = pendingChallengeSelection != null
                ? pendingChallengeSelection
                : challengePolicy.resolveChallengeSelection(riskLevel);

        log.info("注册风控快照：publicIp={}，ipScore={}，deviceScore={}，totalScore={}，riskLevel={}，challengeType={}，challengeSubType={}",
                publicIp,
                ipScore,
                deviceScore,
                totalScore,
                riskLevel,
                challengeSelection != null ? challengeSelection.type() : null,
                challengeSelection != null ? challengeSelection.subType() : null);
        return new RiskSnapshot(ipScore, deviceScore, totalScore, riskLevel, challengeSelection);
    }
}
