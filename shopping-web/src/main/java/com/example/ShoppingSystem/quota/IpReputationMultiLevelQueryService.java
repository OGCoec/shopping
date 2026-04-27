package com.example.ShoppingSystem.quota;

import com.example.ShoppingSystem.mapper.IpReputationProfileMapper;
import com.example.ShoppingSystem.quota.writeback.IpRiskWritebackOrchestrator;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationEvidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Multi-level IP reputation lookup pipeline: local cache -> Redis -> DB -> upstream APIs.
 * Returns the first usable score or evidence and warms lower layers asynchronously when possible.
 */
@Service
public class IpReputationMultiLevelQueryService {

    private static final Logger log = LoggerFactory.getLogger(IpReputationMultiLevelQueryService.class);

    private static final String SOURCE_CAFFEINE = "CAFFEINE";
    private static final String SOURCE_REDIS = "REDIS";
    private static final String SOURCE_DB = "DB";
    private static final String SOURCE_API = "API";
    private static final String SOURCE_NONE = "NONE";
    private static final String PROVIDER_IP2LOCATION = "IP2LOCATION_IO";
    private static final String PROVIDER_IPING = "IPING_CC";

    private static final int SCORE_MIN = 0;
    private static final int SCORE_MAX = 10000;
    private static final int DEFAULT_FRAUD_SCORE_WHEN_MISSING = 50;
    private static final int FRAUD_PENALTY_MULTIPLIER = 80;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final IpReputationProfileMapper ipReputationProfileMapper;
    private final Ip2LocationQuotaHttpService ip2LocationQuotaHttpService;
    private final IpingApiHttpService ipingApiHttpService;
    private final IpRiskLocalCacheStore localCacheStore;
    private final IpRiskWritebackOrchestrator writebackOrchestrator;

    @Value("${register.ip-risk-multi-level.enabled:true}")
    private boolean enabled;

    @Value("${register.ip-risk-multi-level.redis-key-prefix:register:ip:risk:v2:}")
    private String redisKeyPrefix;

    @Value("${register.ip-risk-multi-level.db-expire-hours:24}")
    private int dbExpireHours;

    public IpReputationMultiLevelQueryService(StringRedisTemplate stringRedisTemplate,
                                              ObjectMapper objectMapper,
                                              IpReputationProfileMapper ipReputationProfileMapper,
                                              Ip2LocationQuotaHttpService ip2LocationQuotaHttpService,
                                              IpingApiHttpService ipingApiHttpService,
                                              IpRiskLocalCacheStore localCacheStore,
                                              IpRiskWritebackOrchestrator writebackOrchestrator) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ipReputationProfileMapper = ipReputationProfileMapper;
        this.ip2LocationQuotaHttpService = ip2LocationQuotaHttpService;
        this.ipingApiHttpService = ipingApiHttpService;
        this.localCacheStore = localCacheStore;
        this.writebackOrchestrator = writebackOrchestrator;
    }

    public MultiLevelQueryResult queryEvidence(String publicIp) {
        if (!enabled) {
            return MultiLevelQueryResult.failed(SOURCE_NONE, "multi_level_disabled");
        }
        if (isBlank(publicIp)) {
            return MultiLevelQueryResult.failed(SOURCE_NONE, "invalid_ip");
        }

        String ip = publicIp.trim();
        long now = System.currentTimeMillis();

        IpRiskLocalCacheStore.LocalRiskSnapshot localRisk = localCacheStore.getRisk(ip);
        if (localRisk != null) {
            return MultiLevelQueryResult.success(
                    SOURCE_CAFFEINE,
                    IpReputationEvidence.scoreOnly(localRisk.score()),
                    localRisk.score());
        }

        RiskCacheHit redisHit = readRiskFromRedis(ip);
        if (redisHit != null) {
            localCacheStore.putRisk(ip, redisHit.score(), redisHit.country());
            return MultiLevelQueryResult.success(
                    SOURCE_REDIS,
                    IpReputationEvidence.scoreOnly(redisHit.score()),
                    redisHit.score());
        }

        DbHit dbHit = readFromDb(ip, now);
        if (dbHit != null) {
            localCacheStore.putRisk(ip, dbHit.score(), dbHit.country());
            if (dbHit.payload() != null) {
                // Warm Redis asynchronously on DB hit.
                writebackOrchestrator.orchestrate(SOURCE_DB, ip, dbHit.payload());
            }
            IpReputationEvidence evidence = dbHit.evidence() != null
                    ? dbHit.evidence().withResolvedScore(dbHit.score())
                    : IpReputationEvidence.scoreOnly(dbHit.score());
            return MultiLevelQueryResult.success(SOURCE_DB, evidence, dbHit.score());
        }

        IpRiskCachedPayload fromApi = readFromApi(ip, now);
        if (fromApi != null) {
            localCacheStore.putRisk(ip, fromApi.currentScore(), fromApi.country());
            writebackOrchestrator.orchestrate(SOURCE_API, ip, fromApi);
            return MultiLevelQueryResult.success(SOURCE_API, toEvidence(fromApi), fromApi.currentScore());
        }

        return MultiLevelQueryResult.failed(SOURCE_API, "api_failed_or_blocked");
    }

    private RiskCacheHit readRiskFromRedis(String ip) {
        try {
            String value = stringRedisTemplate.opsForValue().get(redisKey(ip));
            if (isBlank(value)) {
                return null;
            }

            String trimmed = value.trim();
            if (trimmed.startsWith("{")) {
                JsonNode root = objectMapper.readTree(trimmed);
                Integer score = readIntNode(root, "score");
                if (score == null) {
                    score = readIntNode(root, "currentScore");
                }
                if (score == null) {
                    stringRedisTemplate.delete(redisKey(ip));
                    return null;
                }
                String country = normalizeCountryCode(readTextNode(root, "country"));
                if (country == null) {
                    country = normalizeCountryCode(readTextNode(root, "countryCode"));
                }
                return new RiskCacheHit(
                        clamp(score, SCORE_MIN, SCORE_MAX),
                        country);
            }

            Integer score = toInt(trimmed);
            if (score == null) {
                // Drop malformed legacy cache content to avoid repeated parse failures.
                stringRedisTemplate.delete(redisKey(ip));
                return null;
            }
            return new RiskCacheHit(
                    clamp(score, SCORE_MIN, SCORE_MAX),
                    null);
        } catch (Exception e) {
            log.debug("IP risk Redis cache read failed, ip={}, reason={}", ip, e.getMessage());
            return null;
        }
    }

    private DbHit readFromDb(String ip, long now) {
        try {
            Map<String, Object> row = ip.contains(":")
                    ? ipReputationProfileMapper.findIpv6RiskCacheByIp(ip)
                    : ipReputationProfileMapper.findIpv4RiskCacheByIp(ip);
            if (row == null || row.isEmpty()) {
                return null;
            }

            Integer dbScore = toInt(row.get("current_score"));
            if (dbScore == null) {
                return null;
            }
            int score = clamp(dbScore, SCORE_MIN, SCORE_MAX);
            String country = normalizeCountryCode(toStringValue(row.get("country")));

            Long expiresAtEpochMillis = toLong(row.get("expires_at_epoch_millis"));
            if (expiresAtEpochMillis == null || expiresAtEpochMillis <= now) {
                return null;
            }

            String rawJson = toStringValue(row.get("raw_json_text"));
            if (isBlank(rawJson)) {
                IpRiskCachedPayload payload = buildDbMinimalPayload(score, country, expiresAtEpochMillis, now);
                return new DbHit(score, null, payload, country);
            }

            try {
                JsonNode rawNode = objectMapper.readTree(rawJson);
                IpRiskCachedPayload payload = objectMapper.treeToValue(rawNode, IpRiskCachedPayload.class);
                if (payload.country() == null && country != null) {
                    payload = payload.withCountry(country);
                }
                int recalculatedScore = calculateScore(normalize(payload));
                payload = payload.withCurrentScore(recalculatedScore);
                return new DbHit(recalculatedScore, toEvidence(payload), payload, payload.country());
            } catch (Exception parseException) {
                log.debug("IP risk DB payload parse failed, ip={}, reason={}", ip, parseException.getMessage());
                IpRiskCachedPayload payload = buildDbMinimalPayload(score, country, expiresAtEpochMillis, now);
                return new DbHit(score, null, payload, country);
            }
        } catch (Exception e) {
            log.debug("IP risk DB lookup failed, ip={}, reason={}", ip, e.getMessage());
            return null;
        }
    }

    private IpRiskCachedPayload readFromApi(String ip, long now) {
        Ip2LocationQuotaHttpService.Ip2LocationQueryResult ip2LocationResult = ip2LocationQuotaHttpService.queryByIp(ip);
        if (ip2LocationResult != null && ip2LocationResult.success() && ip2LocationResult.riskFields() != null) {
            return toCachedPayload(ip2LocationResult.riskFields(), now, PROVIDER_IP2LOCATION);
        }

        if (shouldFallbackToIping(ip2LocationResult)) {
            IpingApiHttpService.IpingQueryResult ipingResult = ipingApiHttpService.queryByIp(ip);
            if (ipingResult.success() && ipingResult.riskFields() != null) {
                log.info("IP reputation fallback succeeded, ip={}, primaryProvider={}, primaryReason={}, fallbackProvider={}, fallbackReason={}",
                        ip,
                        PROVIDER_IP2LOCATION,
                        ip2LocationResult.reason(),
                        PROVIDER_IPING,
                        ipingResult.reason());
                return toCachedPayload(ipingResult.riskFields(), now, PROVIDER_IPING);
            }
            log.warn("IP reputation fallback failed, ip={}, primaryProvider={}, primaryReason={}, fallbackProvider={}, fallbackReason={}",
                    ip,
                    PROVIDER_IP2LOCATION,
                    ip2LocationResult != null ? ip2LocationResult.reason() : "null_result",
                    PROVIDER_IPING,
                    ipingResult.reason());
        }
        return null;
    }

    private IpRiskCachedPayload toCachedPayload(Ip2LocationQuotaHttpService.RiskRelevantFields fields,
                                                long now,
                                                String sourceProvider) {
        NormalizedRisk normalized = normalize(fields);
        int score = calculateScore(normalized);
        long expiresAt = now + Duration.ofHours(Math.max(1, dbExpireHours)).toMillis();
        return new IpRiskCachedPayload(
                score,
                normalized.fraudScore(),
                normalized.usageType(),
                normalized.proxyType(),
                normalized.asUsageType(),
                normalized.addressType(),
                normalized.isProxy(),
                normalized.isTor(),
                normalized.isPublicProxy(),
                normalized.isWebProxy(),
                normalized.isVpn(),
                normalized.isDataCenter(),
                normalized.isResidentialProxy(),
                normalized.isConsumerPrivacyNetwork(),
                normalized.isEnterprisePrivateNetwork(),
                normalized.asn(),
                normalized.providerName(),
                normalized.country(),
                normalized.latitude(),
                normalized.longitude(),
                expiresAt,
                now,
                sourceProvider
        );
    }

    private boolean shouldFallbackToIping(Ip2LocationQuotaHttpService.Ip2LocationQueryResult ip2LocationResult) {
        return ip2LocationResult != null && ip2LocationResult.blockedByQuota();
    }

    private IpReputationEvidence toEvidence(IpRiskCachedPayload payload) {
        return new IpReputationEvidence(
                true,
                payload.currentScore(),
                payload.fraudScore(),
                payload.usageType(),
                payload.proxyType(),
                payload.proxyIsTor(),
                payload.proxyIsPublicProxy(),
                payload.proxyIsWebProxy(),
                payload.proxyIsVpn(),
                payload.proxyIsDataCenter(),
                payload.proxyIsResidentialProxy(),
                payload.proxyIsConsumerPrivacyNetwork(),
                payload.proxyIsEnterprisePrivateNetwork(),
                payload.asUsageType(),
                payload.addressType()
        );
    }

    private String redisKey(String ip) {
        return redisKeyPrefix + ip;
    }

    private IpRiskCachedPayload buildDbMinimalPayload(int score,
                                                      String country,
                                                      Long expiresAtEpochMillis,
                                                      long now) {
        long safeExpiresAt = (expiresAtEpochMillis != null && expiresAtEpochMillis > now)
                ? expiresAtEpochMillis
                : now + Duration.ofHours(Math.max(1, dbExpireHours)).toMillis();
        return new IpRiskCachedPayload(
                score,
                0,
                "",
                "",
                "",
                "",
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                country,
                null,
                null,
                safeExpiresAt,
                now,
                SOURCE_DB
        );
    }

    private Integer readIntNode(JsonNode root, String field) {
        if (root == null || field == null) {
            return null;
        }
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            return toInt(node.asText());
        }
        return null;
    }

    private String readTextNode(JsonNode root, String field) {
        if (root == null || field == null) {
            return null;
        }
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private NormalizedRisk normalize(Ip2LocationQuotaHttpService.RiskRelevantFields fields) {
        String proxyType = normalizeToken(fields.proxyType());
        boolean isProxy = toBoolean(fields.isProxy());
        boolean isTor = toBoolean(fields.proxyIsTor()) || "TOR".equals(proxyType);
        boolean isPublicProxy = toBoolean(fields.proxyIsPublicProxy()) || "PUB".equals(proxyType);
        boolean isWebProxy = toBoolean(fields.proxyIsWebProxy()) || "WEB".equals(proxyType);
        boolean isVpn = toBoolean(fields.proxyIsVpn()) || "VPN".equals(proxyType);
        boolean isDataCenter = toBoolean(fields.proxyIsDataCenter()) || "DCH".equals(proxyType);
        boolean isResidentialProxy = toBoolean(fields.proxyIsResidentialProxy()) || "RES".equals(proxyType);
        boolean isConsumerPrivacyNetwork = toBoolean(fields.proxyIsConsumerPrivacyNetwork()) || "CPN".equals(proxyType);
        boolean isEnterprisePrivateNetwork = toBoolean(fields.proxyIsEnterprisePrivateNetwork()) || "EPN".equals(proxyType);
        int fraudScore = clamp(parseInt(fields.fraudScore(), DEFAULT_FRAUD_SCORE_WHEN_MISSING), 0, 100);
        return new NormalizedRisk(
                fraudScore,
                normalizeToken(fields.usageType()),
                proxyType,
                normalizeToken(fields.asUsageType()),
                normalizeToken(fields.addressType()),
                normalizeToken(fields.asn()),
                normalizeNullableText(fields.providerName()),
                normalizeCountryCode(fields.countryCode()),
                parseDecimal(fields.latitude()),
                parseDecimal(fields.longitude()),
                isProxy,
                isTor,
                isPublicProxy,
                isWebProxy,
                isVpn,
                isDataCenter,
                isResidentialProxy,
                isConsumerPrivacyNetwork,
                isEnterprisePrivateNetwork
        );
    }

    private NormalizedRisk normalize(IpRiskCachedPayload payload) {
        String proxyType = normalizeToken(payload.proxyType());
        boolean isTor = payload.proxyIsTor() || "TOR".equals(proxyType);
        boolean isPublicProxy = payload.proxyIsPublicProxy() || "PUB".equals(proxyType);
        boolean isWebProxy = payload.proxyIsWebProxy() || "WEB".equals(proxyType);
        boolean isVpn = payload.proxyIsVpn() || "VPN".equals(proxyType);
        boolean isDataCenter = payload.proxyIsDataCenter() || "DCH".equals(proxyType);
        boolean isResidentialProxy = payload.proxyIsResidentialProxy() || "RES".equals(proxyType);
        boolean isConsumerPrivacyNetwork = payload.proxyIsConsumerPrivacyNetwork() || "CPN".equals(proxyType);
        boolean isEnterprisePrivateNetwork = payload.proxyIsEnterprisePrivateNetwork() || "EPN".equals(proxyType);
        int fraudScore = clamp(payload.fraudScore(), 0, 100);
        return new NormalizedRisk(
                fraudScore,
                normalizeToken(payload.usageType()),
                proxyType,
                normalizeToken(payload.asUsageType()),
                normalizeToken(payload.addressType()),
                normalizeToken(payload.asn()),
                normalizeNullableText(payload.providerName()),
                normalizeCountryCode(payload.country()),
                payload.latitude(),
                payload.longitude(),
                payload.isProxy(),
                isTor,
                isPublicProxy,
                isWebProxy,
                isVpn,
                isDataCenter,
                isResidentialProxy,
                isConsumerPrivacyNetwork,
                isEnterprisePrivateNetwork
        );
    }

    private int calculateScore(NormalizedRisk risk) {
        int fraudPenalty = risk.fraudScore() * FRAUD_PENALTY_MULTIPLIER;
        int usageTypePenalty = usageTypePenalty(risk.usageType());
        int proxyTypePenalty = proxyTypePenalty(risk.proxyType());
        int flagPenalty = flagPenalty(risk);
        int asUsagePenalty = asUsagePenalty(risk.asUsageType());
        int trustBonus = trustBonus(risk);

        int referenceScore = 10000
                - fraudPenalty
                - usageTypePenalty
                - proxyTypePenalty
                - flagPenalty
                - asUsagePenalty
                + trustBonus;
        return clamp(referenceScore, SCORE_MIN, SCORE_MAX);
    }

    private int usageTypePenalty(String usageType) {
        return switch (usageType) {
            case "RESIDENTIAL" -> 0;
            case "MOBILE" -> 170;
            case "BUSINESS" -> 340;
            case "UNKNOWN" -> 670;
            case "DCH" -> 1510;
            default -> 670;
        };
    }

    private int proxyTypePenalty(String proxyType) {
        return switch (proxyType) {
            case "VPN" -> 1260;
            case "WEB" -> 1510;
            case "PUB" -> 1850;
            case "TOR" -> 2690;
            case "DCH" -> 1340;
            case "RES" -> 420;
            case "CPN" -> 670;
            case "EPN" -> 500;
            case "SES" -> 1010;
            case "", "-" -> 0;
            default -> 420;
        };
    }

    private int flagPenalty(NormalizedRisk risk) {
        int penalty = 0;
        if (risk.isTor()) {
            penalty += 800;
        }
        if (risk.isPublicProxy()) {
            penalty += 600;
        }
        if (risk.isWebProxy()) {
            penalty += 500;
        }
        if (risk.isVpn()) {
            penalty += 500;
        }
        if (risk.isDataCenter()) {
            penalty += 400;
        }
        if (risk.isResidentialProxy()) {
            penalty += 200;
        }
        if (risk.isConsumerPrivacyNetwork()) {
            penalty += 300;
        }
        if (risk.isEnterprisePrivateNetwork()) {
            penalty += 200;
        }
        return penalty;
    }

    private int asUsagePenalty(String asUsageType) {
        return switch (asUsageType) {
            case "DCH" -> 600;
            case "MOBILE" -> 100;
            case "BUSINESS" -> 200;
            case "UNKNOWN" -> 300;
            case "", "-" -> 0;
            default -> 0;
        };
    }

    private int trustBonus(NormalizedRisk risk) {
        int bonus = 0;
        if ("UNICAST".equals(risk.addressType())) {
            bonus += 100;
        }
        if (risk.fraudScore() <= 5) {
            bonus += 150;
        }
        boolean trustedUsageType = "RESIDENTIAL".equals(risk.usageType()) || "MOBILE".equals(risk.usageType());
        boolean allProxyFlagsFalse = !risk.isVpn()
                && !risk.isTor()
                && !risk.isDataCenter()
                && !risk.isPublicProxy()
                && !risk.isWebProxy()
                && !risk.isResidentialProxy()
                && !risk.isConsumerPrivacyNetwork()
                && !risk.isEnterprisePrivateNetwork();
        if (trustedUsageType && allProxyFlagsFalse) {
            bonus += 300;
        }
        return bonus;
    }

    private boolean toBoolean(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
    }

    private int parseInt(String value, int defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeNullableText(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeToken(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCountryCode(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || "-".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public record MultiLevelQueryResult(boolean success,
                                        String source,
                                        String reason,
                                        IpReputationEvidence evidence,
                                        Integer currentScore) {
        public static MultiLevelQueryResult success(String source, IpReputationEvidence evidence, Integer currentScore) {
            return new MultiLevelQueryResult(true, source, "ok", evidence, currentScore);
        }

        public static MultiLevelQueryResult failed(String source, String reason) {
            return new MultiLevelQueryResult(false, source, reason, null, null);
        }
    }

    private record DbHit(int score,
                         IpReputationEvidence evidence,
                         IpRiskCachedPayload payload,
                         String country) {
    }

    private record RiskCacheHit(int score,
                                String country) {
    }

    private record NormalizedRisk(int fraudScore,
                                  String usageType,
                                  String proxyType,
                                  String asUsageType,
                                  String addressType,
                                  String asn,
                                  String providerName,
                                  String country,
                                  BigDecimal latitude,
                                  BigDecimal longitude,
                                  boolean isProxy,
                                  boolean isTor,
                                  boolean isPublicProxy,
                                  boolean isWebProxy,
                                  boolean isVpn,
                                  boolean isDataCenter,
                                  boolean isResidentialProxy,
                                  boolean isConsumerPrivacyNetwork,
                                  boolean isEnterprisePrivateNetwork) {
    }
}





