package com.example.ShoppingSystem.quota;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * IP2Location.io HTTP 调用服务（带 Redis 配额门禁）。
 */
@Service
public class Ip2LocationQuotaHttpService {

    private static final Logger log = LoggerFactory.getLogger(Ip2LocationQuotaHttpService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final Ip2LocationQuotaService quotaService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiUrl;

    @Autowired
    public Ip2LocationQuotaHttpService(Ip2LocationQuotaService quotaService,
                                       ObjectMapper objectMapper,
                                       @Value("${ip2location.io.api-url:https://api.ip2location.io/}") String apiUrl) {
        this(quotaService, objectMapper, HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(), apiUrl);
    }

    Ip2LocationQuotaHttpService(Ip2LocationQuotaService quotaService,
                                ObjectMapper objectMapper,
                                HttpClient httpClient,
                                String apiUrl) {
        this.quotaService = quotaService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiUrl = apiUrl;
    }

    /**
     * 按 IP 查询 IP2Location。
     * <p>
     * 控制台日志会打印：
     * 1) quota 拦截状态；
     * 2) 原始 payload；
     * 3) riskFields 提取结果；
     * 4) 失败原因与是否回补配额。
     */
    public Ip2LocationQueryResult queryByIp(String ip) {
        if (isBlank(ip)) {
            return Ip2LocationQueryResult.failed(
                    "invalid_ip",
                    null,
                    quotaService.getTotalQuotaCount(),
                    0,
                    false);
        }

        Ip2LocationQuotaService.QuotaAcquireResult acquireResult = quotaService.acquireQuotaForCall();
        if (!acquireResult.allowCall()) {
            log.info("IP2Location查询被配额拦截：ip={}，reason={}，totalQuotaCount={}",
                    ip,
                    acquireResult.reason(),
                    acquireResult.totalQuotaCount());
            return Ip2LocationQueryResult.blocked(acquireResult.reason(), acquireResult.totalQuotaCount());
        }

        String quotaKey = acquireResult.quotaKey();
        String apiKey = extractApiKey(quotaKey);
        if (isBlank(apiKey)) {
            safeCompensate(quotaKey);
            log.warn("IP2Location查询失败：quotaKey无效，ip={}，quotaKey={}", ip, quotaKey);
            return Ip2LocationQueryResult.failed(
                    "invalid_quota_key",
                    quotaKey,
                    quotaService.getTotalQuotaCount(),
                    0,
                    true);
        }

        HttpRequest request = buildRequest(apiKey, ip.trim());
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                safeCompensate(quotaKey);
                log.warn("IP2Location查询HTTP失败：ip={}，quotaKey={}，status={}，responseBody={}",
                        ip,
                        quotaKey,
                        statusCode,
                        response.body());
                return Ip2LocationQueryResult.failed(
                        "http_status_" + statusCode,
                        quotaKey,
                        quotaService.getTotalQuotaCount(),
                        statusCode,
                        true);
            }

            JsonNode payload = objectMapper.readTree(response.body());
            RiskRelevantFields riskFields = extractRiskFields(payload);

            log.info("IP2Location查询成功：ip={}，quotaKey={}，httpStatus={}，riskFields={}",
                    ip,
                    quotaKey,
                    statusCode,
                    formatRiskFields(riskFields));
            log.info("IP2Location原始响应：ip={}，payload={}", ip, payload.toString());

            return Ip2LocationQueryResult.succeeded(
                    quotaKey,
                    quotaService.getTotalQuotaCount(),
                    statusCode,
                    payload,
                    riskFields);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            safeCompensate(quotaKey);
            log.warn("IP2Location查询异常：ip={}，quotaKey={}，reason={}", ip, quotaKey, e.getMessage());
            return Ip2LocationQueryResult.failed(
                    "http_error",
                    quotaKey,
                    quotaService.getTotalQuotaCount(),
                    0,
                    true);
        }
    }

    private HttpRequest buildRequest(String apiKey, String ip) {
        String separator = apiUrl.contains("?") ? "&" : "?";
        String url = apiUrl
                + separator
                + "key="
                + urlEncode(apiKey)
                + "&ip="
                + urlEncode(ip);
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
    }

    private RiskRelevantFields extractRiskFields(JsonNode payload) {
        return new RiskRelevantFields(
                text(payload, "fraud_score"),
                text(payload, "is_proxy"),
                text(payload, "usage_type"),
                text(payload, "address_type"),
                text(payload, "asn"),
                pickProviderName(payload),
                pickCountryCode(payload),
                text(payload, "latitude"),
                text(payload, "longitude"),
                nestedText(payload, "proxy", "proxy_type"),
                nestedText(payload, "proxy", "threat"),
                nestedText(payload, "proxy", "is_vpn"),
                nestedText(payload, "proxy", "is_tor"),
                nestedText(payload, "proxy", "is_data_center"),
                nestedText(payload, "proxy", "is_public_proxy"),
                nestedText(payload, "proxy", "is_residential_proxy"),
                nestedText(payload, "proxy", "is_web_proxy"),
                nestedText(payload, "proxy", "is_consumer_privacy_network"),
                nestedText(payload, "proxy", "is_enterprise_private_network"),
                nestedText(payload, "as_info", "as_usage_type"));
    }

    private String formatRiskFields(RiskRelevantFields fields) {
        if (fields == null) {
            return "null";
        }
        return "fraudScore=" + fields.fraudScore()
                + ", isProxy=" + fields.isProxy()
                + ", usageType=" + fields.usageType()
                + ", addressType=" + fields.addressType()
                + ", asn=" + fields.asn()
                + ", providerName=" + fields.providerName()
                + ", countryCode=" + fields.countryCode()
                + ", latitude=" + fields.latitude()
                + ", longitude=" + fields.longitude()
                + ", proxyType=" + fields.proxyType()
                + ", proxyThreat=" + fields.proxyThreat()
                + ", proxyIsVpn=" + fields.proxyIsVpn()
                + ", proxyIsTor=" + fields.proxyIsTor()
                + ", proxyIsDataCenter=" + fields.proxyIsDataCenter()
                + ", proxyIsPublicProxy=" + fields.proxyIsPublicProxy()
                + ", proxyIsResidentialProxy=" + fields.proxyIsResidentialProxy()
                + ", proxyIsWebProxy=" + fields.proxyIsWebProxy()
                + ", proxyIsConsumerPrivacyNetwork=" + fields.proxyIsConsumerPrivacyNetwork()
                + ", proxyIsEnterprisePrivateNetwork=" + fields.proxyIsEnterprisePrivateNetwork()
                + ", asUsageType=" + fields.asUsageType();
    }

    private String extractApiKey(String quotaKey) {
        if (isBlank(quotaKey)) {
            return null;
        }
        int index = quotaKey.lastIndexOf(':');
        if (index < 0 || index == quotaKey.length() - 1) {
            return null;
        }
        return quotaKey.substring(index + 1).trim();
    }

    private void safeCompensate(String quotaKey) {
        if (isBlank(quotaKey)) {
            return;
        }
        try {
            quotaService.compensateQuota(quotaKey);
        } catch (RuntimeException ex) {
            log.warn("Compensate quota failed for key={}, reason={}", quotaKey, ex.getMessage());
        }
    }

    private String text(JsonNode payload, String fieldName) {
        if (payload == null || fieldName == null) {
            return null;
        }
        JsonNode node = payload.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private String nestedText(JsonNode payload, String objectName, String fieldName) {
        if (payload == null || objectName == null || fieldName == null) {
            return null;
        }
        JsonNode objectNode = payload.get(objectName);
        if (objectNode == null || objectNode.isNull()) {
            return null;
        }
        JsonNode fieldNode = objectNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }

    private String pickProviderName(JsonNode payload) {
        String as = text(payload, "as");
        if (!isBlank(as)) {
            return as.trim();
        }
        String isp = text(payload, "isp");
        if (!isBlank(isp)) {
            return isp.trim();
        }
        String proxyProvider = nestedText(payload, "proxy", "provider");
        if (!isBlank(proxyProvider)) {
            return proxyProvider.trim();
        }
        return null;
    }

    private String pickCountryCode(JsonNode payload) {
        String countryCode = text(payload, "country_code");
        if (!isBlank(countryCode)) {
            return normalizeCountryCode(countryCode);
        }
        String nestedCountryCode = nestedText(payload, "country", "code");
        if (!isBlank(nestedCountryCode)) {
            return normalizeCountryCode(nestedCountryCode);
        }
        return null;
    }

    private String normalizeCountryCode(String countryCode) {
        if (isBlank(countryCode)) {
            return null;
        }
        String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String urlEncode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

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
