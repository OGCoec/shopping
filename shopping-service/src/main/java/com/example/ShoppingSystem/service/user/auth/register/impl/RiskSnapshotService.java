package com.example.ShoppingSystem.service.user.auth.register.impl;

import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import com.example.ShoppingSystem.service.user.auth.register.model.RiskSnapshot;
import com.example.ShoppingSystem.service.user.auth.risk.AuthRiskSnapshot;
import com.example.ShoppingSystem.service.user.auth.risk.AuthRiskSnapshotService;
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

    private final AuthRiskSnapshotService authRiskSnapshotService;
    private final ChallengePolicy challengePolicy;

    public RiskSnapshotService(AuthRiskSnapshotService authRiskSnapshotService,
                               ChallengePolicy challengePolicy) {
        this.authRiskSnapshotService = authRiskSnapshotService;
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
    public RiskSnapshot buildRiskSnapshot(String publicIp,
                                          String deviceFingerprint,
                                          ChallengeSelection pendingChallengeSelection) {
        return buildRiskSnapshot(publicIp, deviceFingerprint, pendingChallengeSelection, null);
    }

    public RiskSnapshot buildRiskSnapshot(String publicIp,
                                          String deviceFingerprint,
                                          ChallengeSelection pendingChallengeSelection,
                                          AuthRiskSnapshot riskSnapshotOverride) {
        AuthRiskSnapshot authRiskSnapshot = validOverride(riskSnapshotOverride)
                ? riskSnapshotOverride
                : authRiskSnapshotService.buildRiskSnapshot(publicIp, deviceFingerprint);
        ChallengeSelection challengeSelection = pendingChallengeSelection != null
                ? pendingChallengeSelection
                : challengePolicy.resolveChallengeSelection(authRiskSnapshot.riskLevel());

        log.info("注册风控快照：publicIp={}，ipScore={}，deviceScore={}，totalScore={}，riskLevel={}，challengeType={}，challengeSubType={}",
                publicIp,
                authRiskSnapshot.ipScore(),
                authRiskSnapshot.deviceScore(),
                authRiskSnapshot.totalScore(),
                authRiskSnapshot.riskLevel(),
                challengeSelection != null ? challengeSelection.type() : null,
                challengeSelection != null ? challengeSelection.subType() : null);
        return new RiskSnapshot(
                authRiskSnapshot.ipScore(),
                authRiskSnapshot.deviceScore(),
                authRiskSnapshot.totalScore(),
                authRiskSnapshot.riskLevel(),
                challengeSelection);
    }

    private boolean validOverride(AuthRiskSnapshot riskSnapshot) {
        return riskSnapshot != null
                && riskSnapshot.ipScore() >= 0
                && riskSnapshot.deviceScore() >= 0
                && riskSnapshot.totalScore() >= 0
                && riskSnapshot.riskLevel() != null
                && !riskSnapshot.riskLevel().isBlank();
    }
}
