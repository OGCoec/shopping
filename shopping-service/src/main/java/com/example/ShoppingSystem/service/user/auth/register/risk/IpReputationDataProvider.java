package com.example.ShoppingSystem.service.user.auth.register.risk;

/**
 * IP 风控证据提供器。
 * <p>
 * 由上层模块（如 web）实现具体来源（IP2Location、本地库、第三方服务等）。
 * shopping-service 只依赖这个接口，不依赖具体 SDK 或 HTTP 客户端实现。
 */
public interface IpReputationDataProvider {

    /**
     * 按公网 IP 拉取风控证据。
     * <p>
     * 约定：
     * 1) 拉取成功返回 available=true 的证据；
     * 2) 拉取失败、无额度、参数无效等情况统一返回 available=false；
     * 3) 方法内部不抛业务异常给上层，避免影响注册主链路。
     *
     * @param publicIp 请求来源公网 IP
     * @return IP 风控证据（可用或不可用）
     */
    IpReputationEvidence fetchEvidence(String publicIp);
}
