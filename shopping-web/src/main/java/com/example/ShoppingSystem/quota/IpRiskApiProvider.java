package com.example.ShoppingSystem.quota;

import com.fasterxml.jackson.databind.JsonNode;

public interface IpRiskApiProvider {

    String PROVIDER_IP2LOCATION = "ip2location";
    String PROVIDER_IPING = "iping";

    String providerCode();

    IpRiskApiResult queryByIp(String ip);

    enum FailureType {
        NONE,
        INVALID_REQUEST,
        QUOTA_BLOCKED,
        PROVIDER_DISABLED,
        HTTP_STATUS,
        BUSINESS_CODE,
        EMPTY_PAYLOAD,
        IO_ERROR
    }

    record IpRiskApiResult(boolean success,
                           FailureType failureType,
                           String reason,
                           String providerCode,
                           int httpStatus,
                           JsonNode payload,
                           RiskRelevantFields riskFields) {

        public static IpRiskApiResult succeeded(String providerCode,
                                                int httpStatus,
                                                JsonNode payload,
                                                RiskRelevantFields riskFields) {
            return new IpRiskApiResult(
                    true,
                    FailureType.NONE,
                    "ok",
                    providerCode,
                    httpStatus,
                    payload,
                    riskFields);
        }

        public static IpRiskApiResult failed(String providerCode,
                                             FailureType failureType,
                                             String reason,
                                             int httpStatus) {
            return new IpRiskApiResult(
                    false,
                    failureType,
                    reason,
                    providerCode,
                    httpStatus,
                    null,
                    null);
        }
    }

    record RiskRelevantFields(String fraudScore,
                              String isProxy,
                              String usageType,
                              String addressType,
                              String asn,
                              String providerName,
                              String countryCode,
                              String region,
                              String city,
                              String latitude,
                              String longitude,
                              String proxyType,
                              String proxyThreat,
                              String proxyIsVpn,
                              String proxyIsTor,
                              String proxyIsDataCenter,
                              String proxyIsPublicProxy,
                              String proxyIsResidentialProxy,
                              String proxyIsWebProxy,
                              String proxyIsConsumerPrivacyNetwork,
                              String proxyIsEnterprisePrivateNetwork,
                              String asUsageType) {
    }
}
