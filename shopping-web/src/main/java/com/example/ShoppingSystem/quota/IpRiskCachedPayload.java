package com.example.ShoppingSystem.quota;

import java.math.BigDecimal;

/**
 * Unified cached payload for IP reputation evidence across local cache, Redis, DB, and MQ writeback.
 */
public record IpRiskCachedPayload(int currentScore,
                                  int fraudScore,
                                  String usageType,
                                  String proxyType,
                                  String asUsageType,
                                  String addressType,
                                  boolean isProxy,
                                  boolean proxyIsTor,
                                  boolean proxyIsPublicProxy,
                                  boolean proxyIsWebProxy,
                                  boolean proxyIsVpn,
                                  boolean proxyIsDataCenter,
                                  boolean proxyIsResidentialProxy,
                                  boolean proxyIsConsumerPrivacyNetwork,
                                  boolean proxyIsEnterprisePrivateNetwork,
                                  String asn,
                                  String providerName,
                                  String country,
                                  BigDecimal latitude,
                                  BigDecimal longitude,
                                  long expiresAtEpochMillis,
                                  long queriedAtEpochMillis,
                                  String sourceProvider) {

    public IpRiskCachedPayload withCurrentScore(int score) {
        return new IpRiskCachedPayload(
                score,
                fraudScore,
                usageType,
                proxyType,
                asUsageType,
                addressType,
                isProxy,
                proxyIsTor,
                proxyIsPublicProxy,
                proxyIsWebProxy,
                proxyIsVpn,
                proxyIsDataCenter,
                proxyIsResidentialProxy,
                proxyIsConsumerPrivacyNetwork,
                proxyIsEnterprisePrivateNetwork,
                asn,
                providerName,
                country,
                latitude,
                longitude,
                expiresAtEpochMillis,
                queriedAtEpochMillis,
                sourceProvider
        );
    }

    public IpRiskCachedPayload withCountry(String normalizedCountry) {
        return new IpRiskCachedPayload(
                currentScore,
                fraudScore,
                usageType,
                proxyType,
                asUsageType,
                addressType,
                isProxy,
                proxyIsTor,
                proxyIsPublicProxy,
                proxyIsWebProxy,
                proxyIsVpn,
                proxyIsDataCenter,
                proxyIsResidentialProxy,
                proxyIsConsumerPrivacyNetwork,
                proxyIsEnterprisePrivateNetwork,
                asn,
                providerName,
                normalizedCountry,
                latitude,
                longitude,
                expiresAtEpochMillis,
                queriedAtEpochMillis,
                sourceProvider
        );
    }
}
