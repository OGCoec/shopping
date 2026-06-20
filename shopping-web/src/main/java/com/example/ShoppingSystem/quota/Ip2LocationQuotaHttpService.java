package com.example.ShoppingSystem.quota;

import com.example.ShoppingSystem.redisdata.Ip2LocationQuotaRedisKeys;
import com.example.ShoppingSystem.redisdata.Ip2LocationQuotaRedisKeys.AccountType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

public interface Ip2LocationQuotaHttpService {
    public record Ip2LocationQueryResult(boolean success,
                                             boolean blockedByQuota,
                                             String reason,
                                             String quotaKey,
                                             long totalQuotaCount,
                                             int httpStatus,
                                             boolean compensated,
                                             JsonNode payload,
                                             RiskRelevantFields riskFields) {

            public static Ip2LocationQueryResult blocked(String reason, long totalQuotaCount) {
                return new Ip2LocationQueryResult(
                        false,
                        true,
                        reason,
                        null,
                        totalQuotaCount,
                        0,
                        false,
                        null,
                        null);
            }

            public static Ip2LocationQueryResult failed(String reason,
                                                        String quotaKey,
                                                        long totalQuotaCount,
                                                        int httpStatus,
                                                        boolean compensated) {
                return new Ip2LocationQueryResult(
                        false,
                        false,
                        reason,
                        quotaKey,
                        totalQuotaCount,
                        httpStatus,
                        compensated,
                        null,
                        null);
            }

            public static Ip2LocationQueryResult succeeded(String quotaKey,
                                                           long totalQuotaCount,
                                                           int httpStatus,
                                                           JsonNode payload,
                                                           RiskRelevantFields riskFields) {
                return new Ip2LocationQueryResult(
                        true,
                        false,
                        "ok",
                        quotaKey,
                        totalQuotaCount,
                        httpStatus,
                        false,
                        payload,
                        riskFields);
            }
        }

    public record RiskRelevantFields(String fraudScore,
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

    public Ip2LocationQueryResult queryByIp(String ip);
}
