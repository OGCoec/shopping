package com.example.ShoppingSystem.filter.preauth.model;

import java.util.List;

/**
 * Redis 中持久化保存的完整预登录绑定对象。
 * <p>
 * 它描述的是“某个 PREAUTH_TOKEN 当前绑定了什么设备、什么 UA、什么 IP、
 * 最近发生过几次 IP 变化，以及当前的风险分与风险等级”。
 */
public record PreAuthBinding(String token,
                             // 预登录令牌本体，作为整条绑定关系的主键。
                             String fpHash,
                             // 设备指纹的 SHA-256，用于校验是否为同一浏览器环境。
                             String uaHash,
                             // User-Agent 的 SHA-256，用于发现浏览器环境变化。
                             String currentIp,
                             // 当前绑定的真实客户端 IP。
                             List<String> recentIps,
                             // 最近出现过的 IP 列表，按“最新优先”排序。
                             int changeCount,
                             // 自创建以来 IP 发生变化的次数。
                             long lastSeenEpochMillis,
                             // 最近一次成功校验通过的时间戳。
                             long expiresAtEpochMillis,
                             // 绑定在 Redis 中的逻辑过期时间。
                             int score,
                             // 当前 IP 对应的风险分。
                             String riskLevel) {
    // record 本身只负责承载数据，不在这里放行为逻辑。
}
