package com.example.ShoppingSystem.service.user.auth.register.model;

/**
 * 单次注册请求的风险快照。
 *
 * @param ipScore IP 风险分
 * @param deviceScore 设备风险分
 * @param totalScore 综合风险分
 * @param riskLevel 风险等级（L1~L6）
 * @param challengeSelection 本轮应使用的挑战选择结果
 */
public record RiskSnapshot(int ipScore,
                           int deviceScore,
                           int totalScore,
                           String riskLevel,
                           ChallengeSelection challengeSelection) {
}
