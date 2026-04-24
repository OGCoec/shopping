package com.example.ShoppingSystem.service.user.auth.register.risk;

/**
 * IP 风控分数服务。
 * <p>
 * 对外只暴露“输入 publicIp，输出 0~10000 分数”的稳定接口，
 * 便于注册流程、登录流程等复用同一套评分能力。
 */
public interface IpReputationScoreService {

    /**
     * 按当前配置计算 IP 初始分（0~10000）。
     * <p>
     * 建议语义：
     * 1) 分数越高表示越可信；
     * 2) 对外永远返回边界内整数；
     * 3) 当外部证据不可用时返回服务内定义的保底分。
     *
     * @param publicIp 请求来源公网 IP
     * @return 0~10000 的 IP 初始分
     */
    int calculateIpScore(String publicIp);
}
