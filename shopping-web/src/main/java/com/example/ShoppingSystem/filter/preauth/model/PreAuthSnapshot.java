package com.example.ShoppingSystem.filter.preauth.model;

/**
 * 返回给前端的轻量级预登录快照。
 * <p>
 * 和 PreAuthBinding 相比，它不会暴露内部存储细节，只保留前端真正关心的信息。
 */
public record PreAuthSnapshot(String token,
                              // 返回给浏览器持有的 PREAUTH_TOKEN。
                              String riskLevel,
                              // 当前风险等级，例如 L3 / L4 / L5 / L6。
                              boolean challengeRequired,
                              // 当前风险等级是否需要额外挑战。
                              boolean blocked,
                              // 当前风险等级是否已经高到直接拒绝访问。
                              long expiresAtEpochMillis) {
    // 纯返回对象，不承担业务逻辑。
}
