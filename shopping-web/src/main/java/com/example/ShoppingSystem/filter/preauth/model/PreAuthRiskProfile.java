package com.example.ShoppingSystem.filter.preauth.model;

/**
 * 预登录风控的最小风险对象。
 * <p>
 * 它只保留流程判断真正需要的两个字段：风险分和风险等级。
 */
public record PreAuthRiskProfile(int score,
                                 // 数值化风险分，通常来自外部 IP 风险查询服务。
                                 String riskLevel) {
    // 纯数据对象，不包含额外逻辑。
}
