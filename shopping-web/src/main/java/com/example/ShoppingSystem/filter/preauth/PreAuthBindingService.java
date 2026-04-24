package com.example.ShoppingSystem.filter.preauth;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.quota.IpReputationMultiLevelQueryService;
import com.example.ShoppingSystem.service.user.auth.register.impl.ChallengePolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 预登录绑定服务：
 * 1) 签发/刷新 preAuthToken；
 * 2) 绑定 fpHash/ip/uaHash；
 * 3) 维护风险等级与挑战门控状态；
 * 4) 统一 Redis TTL 续期。
 */
@Service
public class PreAuthBindingService {

    private static final String FIELD_FP_HASH = "fpHash";
    private static final String FIELD_UA_HASH = "uaHash";
    private static final String FIELD_CURRENT_IP = "currentIp";
    private static final String FIELD_RECENT_IPS = "recentIps";
    private static final String FIELD_CHANGE_COUNT = "changeCount";
    private static final String FIELD_LAST_SEEN = "lastSeen";
    private static final String FIELD_EXPIRES_AT = "expiresAt";
    private static final String FIELD_RISK_LEVEL = "riskLevel";
    private static final String FIELD_SCORE = "score";

    private static final int DEFAULT_SCORE_WHEN_UNAVAILABLE = 6000;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final IpReputationMultiLevelQueryService ipReputationMultiLevelQueryService;
    private final ChallengePolicy challengePolicy;

    @Value("${register.pre-auth.enabled:true}")
    private boolean enabled;

    @Value("${register.pre-auth.redis-key-prefix:register:preauth:bind:}")
    private String redisKeyPrefix;

    @Value("${register.pre-auth.ttl-minutes:60}")
    private int ttlMinutes;

    @Value("${register.pre-auth.recent-ip-limit:3}")
    private int recentIpLimit;

    @Value("${register.pre-auth.cookie-name:PREAUTH_TOKEN}")
    private String cookieName;

    @Value("${register.pre-auth.cookie-path:/}")
    private String cookiePath;

    @Value("${register.pre-auth.cookie-http-only:true}")
    private boolean cookieHttpOnly;

    @Value("${register.pre-auth.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${register.pre-auth.cookie-same-site:Lax}")
    private String cookieSameSite;

    public PreAuthBindingService(StringRedisTemplate stringRedisTemplate,
                                 ObjectMapper objectMapper,
                                 IpReputationMultiLevelQueryService ipReputationMultiLevelQueryService,
                                 ChallengePolicy challengePolicy) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ipReputationMultiLevelQueryService = ipReputationMultiLevelQueryService;
        this.challengePolicy = challengePolicy;
    }

    /**
     * bootstrap：首次签发或“老 token 换新/续期”。
     */
    public PreAuthSnapshot bootstrap(String incomingToken,
                                     String rawFingerprint,
                                     HttpServletRequest request) {
        String normalizedFingerprint = normalizeFingerprint(rawFingerprint, request);
        String fpHash = sha256(normalizedFingerprint);
        String uaHash = sha256(resolveUserAgent(request));
        String ip = resolveClientIp(request);

        if (!enabled) {
            RiskProfile fallbackRisk = resolveRiskProfile(ip);
            long now = System.currentTimeMillis();
            long expiresAt = now + ttl().toMillis();
            return new PreAuthSnapshot(
                    "",
                    fallbackRisk.riskLevel(),
                    isChallengeRequired(fallbackRisk.riskLevel()),
                    isBlockedRisk(fallbackRisk.riskLevel()),
                    expiresAt);
        }

        if (StrUtil.isNotBlank(incomingToken)) {
            PreAuthBinding existing = loadBinding(incomingToken.trim());
            if (existing != null && fpHash.equals(existing.fpHash()) && uaHash.equals(existing.uaHash())) {
                PreAuthBinding refreshed = refreshExistingBinding(existing, ip);
                saveBinding(refreshed);
                return toSnapshot(refreshed);
            }
        }

        String token = IdUtil.nanoId(48);
        long now = System.currentTimeMillis();
        RiskProfile riskProfile = resolveRiskProfile(ip);
        PreAuthBinding created = new PreAuthBinding(
                token,
                fpHash,
                uaHash,
                ip,
                appendRecentIp(new ArrayList<>(), ip),
                0,
                now,
                now + ttl().toMillis(),
                riskProfile.score(),
                riskProfile.riskLevel()
        );
        saveBinding(created);
        return toSnapshot(created);
    }

    /**
     * 过滤器校验入口：校验 token + fp/ua + IP 漂移处理 + 续期。
     */
    public ValidationOutcome validateAndTouch(String token,
                                              String rawFingerprint,
                                              HttpServletRequest request) {
        if (StrUtil.isBlank(token) || !enabled) {
            return ValidationOutcome.invalid(ValidationError.MISSING_TOKEN);
        }

        PreAuthBinding existing = loadBinding(token.trim());
        if (existing == null) {
            return ValidationOutcome.invalid(ValidationError.EXPIRED);
        }

        String normalizedFingerprint = normalizeFingerprint(rawFingerprint, request);
        String fpHash = sha256(normalizedFingerprint);
        if (!fpHash.equals(existing.fpHash())) {
            deleteBinding(existing.token());
            return ValidationOutcome.invalid(ValidationError.FINGERPRINT_MISMATCH);
        }

        String uaHash = sha256(resolveUserAgent(request));
        if (!uaHash.equals(existing.uaHash())) {
            deleteBinding(existing.token());
            return ValidationOutcome.invalid(ValidationError.USER_AGENT_MISMATCH);
        }

        PreAuthBinding updated = refreshExistingBinding(existing, resolveClientIp(request));
        saveBinding(updated);
        return ValidationOutcome.valid(updated);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String resolveIncomingToken(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String cookieToken = resolveTokenFromCookie(request);
        if (StrUtil.isNotBlank(cookieToken)) {
            return cookieToken;
        }
        return StrUtil.blankToDefault(request.getHeader(PreAuthHeaders.HEADER_PREAUTH_TOKEN), "").trim();
    }

    public ResponseCookie buildTokenCookie(String token, HttpServletRequest request) {
        return ResponseCookie.from(cookieName, StrUtil.blankToDefault(token, ""))
                .httpOnly(cookieHttpOnly)
                .secure(cookieSecure || isHttpsRequest(request))
                .path(normalizeCookiePath(cookiePath))
                .sameSite(normalizeSameSite(cookieSameSite))
                .maxAge(ttl())
                .build();
    }

    public ResponseCookie buildExpiredTokenCookie(HttpServletRequest request) {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(cookieHttpOnly)
                .secure(cookieSecure || isHttpsRequest(request))
                .path(normalizeCookiePath(cookiePath))
                .sameSite(normalizeSameSite(cookieSameSite))
                .maxAge(Duration.ZERO)
                .build();
    }

    public boolean isBlockedRisk(String riskLevel) {
        return "L6".equalsIgnoreCase(riskLevel);
    }

    public boolean isChallengeRequired(String riskLevel) {
        if (riskLevel == null) {
            return false;
        }
        String normalized = riskLevel.trim().toUpperCase(Locale.ROOT);
        return "L3".equals(normalized) || "L4".equals(normalized) || "L5".equals(normalized);
    }

    private String resolveTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return "";
        }
        for (Cookie cookie : cookies) {
            if (cookie == null) {
                continue;
            }
            if (!StrUtil.equals(cookieName, cookie.getName())) {
                continue;
            }
            return StrUtil.blankToDefault(cookie.getValue(), "").trim();
        }
        return "";
    }

    private boolean isHttpsRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(StrUtil.blankToDefault(forwardedProto, "").trim());
    }

    private String normalizeCookiePath(String rawPath) {
        String normalized = StrUtil.blankToDefault(rawPath, "/").trim();
        if (normalized.isEmpty()) {
            return "/";
        }
        if (!normalized.startsWith("/")) {
            return "/" + normalized;
        }
        return normalized;
    }

    private String normalizeSameSite(String rawSameSite) {
        String normalized = StrUtil.blankToDefault(rawSameSite, "Lax").trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> "Lax";
        };
    }

    private PreAuthBinding refreshExistingBinding(PreAuthBinding existing, String currentIp) {
        long now = System.currentTimeMillis();
        boolean ipChanged = !StrUtil.equals(existing.currentIp(), currentIp);
        int changeCount = existing.changeCount();
        List<String> recentIps = new ArrayList<>(existing.recentIps());
        String riskLevel = existing.riskLevel();
        int score = existing.score();

        if (ipChanged) {
            recentIps = appendRecentIp(recentIps, currentIp);
            changeCount += 1;
            RiskProfile riskProfile = resolveRiskProfile(currentIp);
            score = riskProfile.score();
            riskLevel = riskProfile.riskLevel();
        }

        return new PreAuthBinding(
                existing.token(),
                existing.fpHash(),
                existing.uaHash(),
                currentIp,
                recentIps,
                changeCount,
                now,
                now + ttl().toMillis(),
                score,
                riskLevel
        );
    }

    private RiskProfile resolveRiskProfile(String ip) {
        int score = DEFAULT_SCORE_WHEN_UNAVAILABLE;
        IpReputationMultiLevelQueryService.MultiLevelQueryResult result = ipReputationMultiLevelQueryService.queryEvidence(ip);
        if (result != null && result.success() && result.currentScore() != null) {
            score = result.currentScore();
        }
        String riskLevel = challengePolicy.resolveRiskLevel(score);
        return new RiskProfile(score, riskLevel);
    }

    private PreAuthBinding loadBinding(String token) {
        try {
            Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(redisKey(token));
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            return new PreAuthBinding(
                    token,
                    toStringValue(raw.get(FIELD_FP_HASH)),
                    toStringValue(raw.get(FIELD_UA_HASH)),
                    toStringValue(raw.get(FIELD_CURRENT_IP)),
                    parseRecentIps(toStringValue(raw.get(FIELD_RECENT_IPS))),
                    parseInt(toStringValue(raw.get(FIELD_CHANGE_COUNT)), 0),
                    parseLong(toStringValue(raw.get(FIELD_LAST_SEEN)), 0L),
                    parseLong(toStringValue(raw.get(FIELD_EXPIRES_AT)), 0L),
                    parseInt(toStringValue(raw.get(FIELD_SCORE)), DEFAULT_SCORE_WHEN_UNAVAILABLE),
                    toStringValue(raw.get(FIELD_RISK_LEVEL))
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveBinding(PreAuthBinding binding) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put(FIELD_FP_HASH, binding.fpHash());
        hash.put(FIELD_UA_HASH, binding.uaHash());
        hash.put(FIELD_CURRENT_IP, binding.currentIp());
        hash.put(FIELD_RECENT_IPS, stringifyRecentIps(binding.recentIps()));
        hash.put(FIELD_CHANGE_COUNT, String.valueOf(binding.changeCount()));
        hash.put(FIELD_LAST_SEEN, String.valueOf(binding.lastSeenEpochMillis()));
        hash.put(FIELD_EXPIRES_AT, String.valueOf(binding.expiresAtEpochMillis()));
        hash.put(FIELD_SCORE, String.valueOf(binding.score()));
        hash.put(FIELD_RISK_LEVEL, StrUtil.blankToDefault(binding.riskLevel(), "L3"));

        String key = redisKey(binding.token());
        stringRedisTemplate.opsForHash().putAll(key, hash);
        stringRedisTemplate.expire(key, ttl());
    }

    private void deleteBinding(String token) {
        if (StrUtil.isBlank(token)) {
            return;
        }
        stringRedisTemplate.delete(redisKey(token.trim()));
    }

    private String redisKey(String token) {
        return redisKeyPrefix + token;
    }

    private Duration ttl() {
        return Duration.ofMinutes(Math.max(1, ttlMinutes));
    }

    private List<String> appendRecentIp(List<String> recentIps, String ip) {
        if (recentIps == null) {
            recentIps = new ArrayList<>();
        }
        if (StrUtil.isBlank(ip)) {
            return recentIps;
        }
        recentIps.removeIf(existing -> StrUtil.equals(existing, ip));
        recentIps.add(0, ip);
        int safeLimit = Math.max(1, recentIpLimit);
        while (recentIps.size() > safeLimit) {
            recentIps.remove(recentIps.size() - 1);
        }
        return recentIps;
    }

    private List<String> parseRecentIps(String value) {
        if (StrUtil.isBlank(value)) {
            return new ArrayList<>();
        }
        try {
            List<String> parsed = objectMapper.readValue(value, new TypeReference<>() {
            });
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private String stringifyRecentIps(List<String> recentIps) {
        try {
            return objectMapper.writeValueAsString(recentIps == null ? List.of() : recentIps);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private String normalizeFingerprint(String rawFingerprint, HttpServletRequest request) {
        if (StrUtil.isNotBlank(rawFingerprint)) {
            return rawFingerprint.trim();
        }
        String userAgent = resolveUserAgent(request);
        String language = StrUtil.blankToDefault(request.getHeader("Accept-Language"), "unknown");
        return userAgent + "|" + language;
    }

    private String resolveUserAgent(HttpServletRequest request) {
        return StrUtil.blankToDefault(request.getHeader("User-Agent"), "unknown").trim();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(realIp)) {
            return realIp.trim();
        }
        return StrUtil.blankToDefault(request.getRemoteAddr(), "unknown");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(StrUtil.blankToDefault(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return StrUtil.blankToDefault(value, "");
        }
    }

    private int parseInt(String value, int defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private long parseLong(String value, long defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String toStringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private PreAuthSnapshot toSnapshot(PreAuthBinding binding) {
        return new PreAuthSnapshot(
                binding.token(),
                binding.riskLevel(),
                isChallengeRequired(binding.riskLevel()),
                isBlockedRisk(binding.riskLevel()),
                binding.expiresAtEpochMillis()
        );
    }

    private record RiskProfile(int score,
                               String riskLevel) {
    }

    public record PreAuthSnapshot(String token,
                                  String riskLevel,
                                  boolean challengeRequired,
                                  boolean blocked,
                                  long expiresAtEpochMillis) {
    }

    public record ValidationOutcome(boolean valid,
                                    ValidationError error,
                                    PreAuthBinding binding) {
        public static ValidationOutcome valid(PreAuthBinding binding) {
            return new ValidationOutcome(true, ValidationError.NONE, binding);
        }

        public static ValidationOutcome invalid(ValidationError error) {
            return new ValidationOutcome(false, error, null);
        }
    }

    public enum ValidationError {
        NONE,
        MISSING_TOKEN,
        EXPIRED,
        FINGERPRINT_MISMATCH,
        USER_AGENT_MISMATCH
    }

    public record PreAuthBinding(String token,
                                 String fpHash,
                                 String uaHash,
                                 String currentIp,
                                 List<String> recentIps,
                                 int changeCount,
                                 long lastSeenEpochMillis,
                                 long expiresAtEpochMillis,
                                 int score,
                                 String riskLevel) {
    }
}
