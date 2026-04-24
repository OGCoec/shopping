package com.example.ShoppingSystem.service.user.auth.register.risk;

/**
 * IP 风控证据快照。
 * <p>
 * 这是一个与具体数据源解耦的统一证据模型：
 * 无论证据来自本地缓存、Redis、DB 还是外部 API，评分服务都只依赖这个对象。
 */
public record IpReputationEvidence(boolean available,
                                   Integer resolvedScore,
                                   int fraudScore,
                                   String usageType,
                                   String proxyType,
                                   boolean proxyIsTor,
                                   boolean proxyIsPublicProxy,
                                   boolean proxyIsWebProxy,
                                   boolean proxyIsVpn,
                                   boolean proxyIsDataCenter,
                                   boolean proxyIsResidentialProxy,
                                   boolean proxyIsConsumerPrivacyNetwork,
                                   boolean proxyIsEnterprisePrivateNetwork,
                                   String asUsageType,
                                   String addressType) {

    /**
     * 构造“证据不可用”的对象。
     * <p>
     * 评分服务收到该对象后会走保底分逻辑。
     */
    public static IpReputationEvidence unavailable() {
        return new IpReputationEvidence(
                false,
                null,
                0,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null);
    }

    /**
     * 构造“仅有预计算分”的对象。
     * <p>
     * 典型场景：本地缓存/Redis 命中时只拿到 score，没有细项证据。
     */
    public static IpReputationEvidence scoreOnly(int resolvedScore) {
        return new IpReputationEvidence(
                true,
                resolvedScore,
                0,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null);
    }

    /**
     * 是否携带了可直接使用的预计算分。
     */
    public boolean hasResolvedScore() {
        return resolvedScore != null;
    }

    /**
     * 在不改变其他字段的前提下覆盖预计算分。
     */
    public IpReputationEvidence withResolvedScore(int score) {
        return new IpReputationEvidence(
                available,
                score,
                fraudScore,
                usageType,
                proxyType,
                proxyIsTor,
                proxyIsPublicProxy,
                proxyIsWebProxy,
                proxyIsVpn,
                proxyIsDataCenter,
                proxyIsResidentialProxy,
                proxyIsConsumerPrivacyNetwork,
                proxyIsEnterprisePrivateNetwork,
                asUsageType,
                addressType
        );
    }
}
