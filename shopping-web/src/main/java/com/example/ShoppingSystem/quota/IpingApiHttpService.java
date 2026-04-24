package com.example.ShoppingSystem.quota;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * iPing API fallback service.
 * This service is used only as a degrade path when IP2Location quota is exhausted.
 */
@Service
public class IpingApiHttpService {

    private static final Logger log = LoggerFactory.getLogger(IpingApiHttpService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiUrl;
    private final String language;
    private final boolean enabled;

    public IpingApiHttpService(ObjectMapper objectMapper,
                               @Value("${iping.api.enabled:true}") boolean enabled,
                               @Value("${iping.api.url:https://api.iping.cc/v1/query}") String apiUrl,
                               @Value("${iping.api.language:en}") String language) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
        this.enabled = enabled;
        this.apiUrl = apiUrl;
        this.language = language;
    }

    public IpingQueryResult queryByIp(String ip) {
        if (!enabled) {
            return IpingQueryResult.failed("iping_disabled", 0);
        }
        if (isBlank(ip)) {
            return IpingQueryResult.failed("invalid_ip", 0);
        }
        if (ip.contains(":")) {
            // iping public document currently states only IPv4 query is supported.
            return IpingQueryResult.failed("ipv6_not_supported", 0);
        }

        HttpRequest request = HttpRequest.newBuilder(buildUri(ip.trim()))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                log.warn("IPING查询失败：ip={}，httpStatus={}，body={}", ip, statusCode, response.body());
                return IpingQueryResult.failed("http_status_" + statusCode, statusCode);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode payload = pickPayload(root);
            Integer code = readInt(root, "code");
            if (code != null && code != 200) {
                String msg = text(root, "msg");
                log.warn("IPING业务返回非成功：ip={}，code={}，msg={}", ip, code, msg);
                return IpingQueryResult.failed("business_code_" + code, statusCode);
            }
            if (payload == null || payload.isNull() || payload.isMissingNode()) {
                return IpingQueryResult.failed("empty_payload", statusCode);
            }

            Ip2LocationQuotaHttpService.RiskRelevantFields fields = extractRiskFields(payload);
            log.info("IPING查询成功：ip={}，httpStatus={}，riskFields={}", ip, statusCode, formatRiskFields(fields));
            return IpingQueryResult.succeeded(statusCode, payload, fields);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("IPING查询异常：ip={}，reason={}", ip, e.getMessage());
            return IpingQueryResult.failed("http_error", 0);
        }
    }

    private URI buildUri(String ip) {
        String separator = apiUrl.contains("?") ? "&" : "?";
        String url = apiUrl
                + separator
                + "ip=" + urlEncode(ip)
                + "&language=" + urlEncode(language);
        return URI.create(url);
    }

    private JsonNode pickPayload(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode data = root.get("data");
        if (data != null && !data.isNull() && !data.isMissingNode()) {
            return data;
        }
        return root;
    }

    private Ip2LocationQuotaHttpService.RiskRelevantFields extractRiskFields(JsonNode payload) {
        String usageType = normalizeUsageType(text(payload, "usage_type"));
        boolean isDataCenter = "DCH".equals(usageType);
        String isProxy = normalizeBooleanText(text(payload, "is_proxy"));
        String proxyType = isDataCenter ? "DCH" : "";
        String asUsageType = normalizeAsUsageType(text(payload, "as_type"), usageType);
        return new Ip2LocationQuotaHttpService.RiskRelevantFields(
                text(payload, "risk_score"),
                isProxy,
                usageType,
                normalizeAddressType(text(payload, "type")),
                normalizeAsn(text(payload, "asn")),
                pickProviderName(payload),
                pickCountryCode(payload),
                text(payload, "latitude"),
                text(payload, "longitude"),
                proxyType,
                text(payload, "risk_tag"),
                "false",
                "false",
                Boolean.toString(isDataCenter),
                "false",
                "false",
                "false",
                "false",
                "false",
                asUsageType
        );
    }

    private String normalizeUsageType(String usageType) {
        String token = normalizeToken(usageType);
        return switch (token) {
            case "IDC", "DCH", "DATACENTER", "DATA_CENTER", "DATA-CENTER" -> "DCH";
            case "RES", "RESIDENTIAL", "HOME_BROADBAND", "HOME" -> "RESIDENTIAL";
            case "MOBILE", "CELLULAR", "MOB" -> "MOBILE";
            case "BUSINESS", "BIZ", "CORPORATE", "COM" -> "BUSINESS";
            case "", "-" -> "UNKNOWN";
            default -> token;
        };
    }

    private String normalizeAsUsageType(String asType, String usageType) {
        String token = normalizeToken(asType);
        if ("IDC".equals(token) || "DCH".equals(token)) {
            return "DCH";
        }
        if ("MOBILE".equals(token)) {
            return "MOBILE";
        }
        if ("COM".equals(token) || "BUSINESS".equals(token) || "BIZ".equals(token) || "ISP".equals(token)) {
            return "BUSINESS";
        }
        if ("UNKNOWN".equals(token) || token.isEmpty()) {
            return "UNKNOWN";
        }
        if ("DCH".equals(usageType)) {
            return "DCH";
        }
        return "UNKNOWN";
    }

    private String normalizeAddressType(String type) {
        String token = normalizeToken(type);
        return switch (token) {
            case "NATIVE" -> "UNICAST";
            case "", "-" -> "UNKNOWN";
            default -> token;
        };
    }

    private String normalizeAsn(String asn) {
        if (isBlank(asn)) {
            return null;
        }
        String trimmed = asn.trim().toUpperCase(Locale.ROOT);
        if (trimmed.startsWith("AS")) {
            return trimmed.substring(2);
        }
        return trimmed;
    }

    private String pickProviderName(JsonNode payload) {
        String asOwner = text(payload, "as_owner");
        if (!isBlank(asOwner)) {
            return asOwner.trim();
        }
        String company = text(payload, "company");
        if (!isBlank(company)) {
            return company.trim();
        }
        String isp = text(payload, "isp");
        if (!isBlank(isp)) {
            return isp.trim();
        }
        return null;
    }

    private String pickCountryCode(JsonNode payload) {
        String countryCode = text(payload, "country_code");
        if (!isBlank(countryCode)) {
            return normalizeCountryCode(countryCode);
        }
        String country = text(payload, "country");
        if (!isBlank(country) && country.trim().length() == 2) {
            return normalizeCountryCode(country);
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

    private String normalizeBooleanText(String value) {
        if (isBlank(value)) {
            return "false";
        }
        String token = value.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(token) || "true".equals(token) || "yes".equals(token)) {
            return "true";
        }
        return "false";
    }

    private String normalizeToken(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode valueNode = node.get(field);
        if (valueNode == null || valueNode.isNull() || valueNode.isMissingNode()) {
            return null;
        }
        return valueNode.asText();
    }

    private Integer readInt(JsonNode node, String field) {
        String value = text(node, field);
        if (isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatRiskFields(Ip2LocationQuotaHttpService.RiskRelevantFields fields) {
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
                + ", asUsageType=" + fields.asUsageType();
    }

    private String urlEncode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record IpingQueryResult(boolean success,
                                   String reason,
                                   int httpStatus,
                                   JsonNode payload,
                                   Ip2LocationQuotaHttpService.RiskRelevantFields riskFields) {
        public static IpingQueryResult succeeded(int httpStatus,
                                                 JsonNode payload,
                                                 Ip2LocationQuotaHttpService.RiskRelevantFields riskFields) {
            return new IpingQueryResult(true, "ok", httpStatus, payload, riskFields);
        }

        public static IpingQueryResult failed(String reason, int httpStatus) {
            return new IpingQueryResult(false, reason, httpStatus, null, null);
        }
    }
}
