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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
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
 * 预登录绑定服务。
 * 核心职责：
 * 1) 签发/续期 PREAUTH_TOKEN；
 * 2) 维护 token 与设备指纹、UA、IP 的绑定关系；
 * 3) 维护风险分和风险等级，支持拦截器快速决策；
 * 4) 在 IP 漂移场景下，协调 WAF 验证前后状态。
 */
@Service
public class PreAuthBindingService {

    /**
     * Redis Hash 字段：设备指纹哈希。
     */
    private static final String FIELD_FP_HASH = "fpHash";
    /**
     * Redis Hash 字段：UA 哈希。
     */
    private static final String FIELD_UA_HASH = "uaHash";
    /**
     * Redis Hash 字段：当前 IP。
     */
    private static final String FIELD_CURRENT_IP = "currentIp";
    /**
     * Redis Hash 字段：最近 IP 列表（JSON 字符串）。
     */
    private static final String FIELD_RECENT_IPS = "recentIps";
    /**
     * Redis Hash 字段：IP 变化次数。
     */
    private static final String FIELD_CHANGE_COUNT = "changeCount";
    /**
     * Redis Hash 字段：最后访问时间戳。
     */
    private static final String FIELD_LAST_SEEN = "lastSeen";
    /**
     * Redis Hash 字段：过期时间戳。
     */
    private static final String FIELD_EXPIRES_AT = "expiresAt";
    /**
     * Redis Hash 字段：风险等级（如 L3/L4/L5/L6）。
     */
    private static final String FIELD_RISK_LEVEL = "riskLevel";
    /**
     * Redis Hash 字段：风险分。
     */
    private static final String FIELD_SCORE = "score";

    /**
     * 当外部风险查询不可用时的默认风险分。
     */
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

    @Value("${register.pre-auth.waf-required-cookie-name:WAF_REQUIRED}")
    private String wafRequiredCookieName;

    @Value("${register.pre-auth.waf-verified-key-prefix:register:preauth:waf:verified:}")
    private String wafVerifiedKeyPrefix;

    @Value("${register.pre-auth.waf-verified-ttl-minutes:10}")
    private int wafVerifiedTtlMinutes;

    /**
     * 构造函数：注入依赖组件。
     */
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
     * 引导接口：初始化或续期预登录上下文。
     * 逻辑分支：
     * 1) 功能关闭时：仅返回风险快照，不落 token。
     * 2) 有入站 token 且 fp/ua 匹配：复用并续期。
     * 3) 其他情况：签发新 token 并落库。
     */
    public PreAuthSnapshot bootstrap(String incomingToken,
                                     String rawFingerprint,
                                     HttpServletRequest request) {
        // 统一标准化后做哈希，避免在 Redis 直接保存明文指纹/UA。
        String normalizedFingerprint = normalizeFingerprint(rawFingerprint, request);
        String fpHash = sha256(normalizedFingerprint);
        String uaHash = sha256(resolveUserAgent(request));
        String ip = resolveClientIp(request);

        if (!enabled) {
            // 关闭模式：返回前端需要的风险信息，但不做持久化状态。
            RiskProfile fallbackRisk = resolveRiskProfile(ip);
            long now = System.currentTimeMillis();
            long expiresAt = now + ttl().toMillis();
            return new PreAuthSnapshot(
                    "",
                    fallbackRisk.riskLevel(),
                    isChallengeRequired(fallbackRisk.riskLevel()),
                    isBlockedRisk(fallbackRisk.riskLevel()),
                    expiresAt
            );
        }

        if (StrUtil.isNotBlank(incomingToken)) {
            // 只允许同指纹+同 UA 续期，防止 token 在不同环境间被复用。
            PreAuthBinding existing = loadBinding(incomingToken.trim());
            if (existing != null && fpHash.equals(existing.fpHash()) && uaHash.equals(existing.uaHash())) {
                PreAuthBinding refreshed = refreshExistingBinding(existing, ip);
                saveBinding(refreshed);
                return toSnapshot(refreshed);
            }
        }

        // 无有效上下文可续期时，创建新 token 及其绑定关系。
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
     * 拦截器主校验入口。
     * 校验链路：
     * 1) token 是否存在且功能已开启；
     * 2) token 是否在 Redis 中存在；
     * 3) 设备指纹是否匹配；
     * 4) UA 是否匹配；
     * 5) IP 是否漂移，若漂移是否已完成 WAF；
     * 6) 校验通过后刷新上下文并续期。
     */
    public ValidationOutcome validateAndTouch(String token,
                                              String rawFingerprint,
                                              HttpServletRequest request) {
        if (StrUtil.isBlank(token) || !enabled) {
            return ValidationOutcome.invalid(ValidationError.MISSING_TOKEN);
        }

        // 先校验 token 是否存在，过期直接拒绝。
        PreAuthBinding existing = loadBinding(token.trim());
        if (existing == null) {
            return ValidationOutcome.invalid(ValidationError.EXPIRED);
        }

        // 指纹不匹配：视为上下文被替换，删除旧绑定并拒绝。
        String normalizedFingerprint = normalizeFingerprint(rawFingerprint, request);
        String fpHash = sha256(normalizedFingerprint);
        if (!fpHash.equals(existing.fpHash())) {
            deleteBinding(existing.token());
            return ValidationOutcome.invalid(ValidationError.FINGERPRINT_MISMATCH);
        }

        // UA 不匹配：同样删除旧绑定并拒绝。
        String uaHash = sha256(resolveUserAgent(request));
        if (!uaHash.equals(existing.uaHash())) {
            deleteBinding(existing.token());
            return ValidationOutcome.invalid(ValidationError.USER_AGENT_MISMATCH);
        }

        // IP 变化时，必须先有 WAF 验证标记；否则返回“需要 WAF”。
        String currentIp = resolveClientIp(request);
        boolean ipChanged = !StrUtil.equals(existing.currentIp(), currentIp);
        if (ipChanged && !isWafVerifiedForIp(existing.token(), currentIp)) {
            return ValidationOutcome.invalid(ValidationError.IP_CHANGED_WAF_REQUIRED);
        }
        if (ipChanged) {
            // WAF 标记设计为“一次性消费”，防止被重复利用。
            clearWafVerifiedForIp(existing.token(), currentIp);
        }

        // 校验通过后刷新最近访问与过期时间。
        PreAuthBinding updated = refreshExistingBinding(existing, currentIp);
        saveBinding(updated);
        return ValidationOutcome.valid(updated);
    }

    /**
     * 是否启用预登录绑定能力。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 解析入站 token。
     * 优先级：cookie > 请求头。
     */
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

    /**
     * 构建正常 token cookie。
     */
    public ResponseCookie buildTokenCookie(String token, HttpServletRequest request) {
        return ResponseCookie.from(cookieName, StrUtil.blankToDefault(token, ""))
                .httpOnly(cookieHttpOnly)
                .secure(cookieSecure || isHttpsRequest(request))
                .path(normalizeCookiePath(cookiePath))
                .sameSite(normalizeSameSite(cookieSameSite))
                .maxAge(ttl())
                .build();
    }

    /**
     * 构建过期 token cookie（用于清理客户端 cookie）。
     */
    public ResponseCookie buildExpiredTokenCookie(HttpServletRequest request) {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(cookieHttpOnly)
                .secure(cookieSecure || isHttpsRequest(request))
                .path(normalizeCookiePath(cookiePath))
                .sameSite(normalizeSameSite(cookieSameSite))
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * 构建 WAF_REQUIRED 标记 cookie，提示前端需要走 WAF 验证流程。
     */
    public ResponseCookie buildWafRequiredCookie(HttpServletRequest request) {
        return ResponseCookie.from(wafRequiredCookieName, "1")
                .httpOnly(false)
                .secure(cookieSecure || isHttpsRequest(request))
                .path(normalizeCookiePath(cookiePath))
                .sameSite(normalizeSameSite(cookieSameSite))
                .maxAge(Duration.ofMinutes(Math.max(1, wafVerifiedTtlMinutes)))
                .build();
    }

    /**
     * 构建清理 WAF_REQUIRED 的 cookie。
     */
    public ResponseCookie buildClearWafRequiredCookie(HttpServletRequest request) {
        return ResponseCookie.from(wafRequiredCookieName, "")
                .httpOnly(false)
                .secure(cookieSecure || isHttpsRequest(request))
                .path(normalizeCookiePath(cookiePath))
                .sameSite(normalizeSameSite(cookieSameSite))
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * 将“当前 token + 当前 IP”打上 WAF 已验证标记。
     */
    public void markWafVerifiedForCurrentIp(String token, HttpServletRequest request) {
        if (StrUtil.isBlank(token) || request == null) {
            return;
        }
        String currentIp = resolveClientIp(request);
        markWafVerifiedForIp(token.trim(), currentIp);
    }

    /**
     * WAF 验证通过后的统一处理：
     * 1) 记录当前 token + 当前 IP 的短时验证标记；
     * 2) 若绑定仍存在，则立即把 currentIp/score/riskLevel 同步为当前 IP 的结果。
     * 说明：这样可以避免“必须等下一次受保护请求”才更新风险信息。
     */
    public void markWafVerifiedAndRefreshBindingForCurrentIp(String token, HttpServletRequest request) {
        if (StrUtil.isBlank(token) || request == null) {
            return;
        }
        String normalizedToken = token.trim();
        String currentIp = resolveClientIp(request);
        markWafVerifiedForIp(normalizedToken, currentIp);

        PreAuthBinding existing = loadBinding(normalizedToken);
        if (existing == null) {
            return;
        }
        PreAuthBinding refreshed = refreshExistingBinding(existing, currentIp);
        saveBinding(refreshed);
    }

    /**
     * 风险等级是否应直接阻断。
     * 当前策略：L6 直接阻断。
     */
    public boolean isBlockedRisk(String riskLevel) {
        return "L6".equalsIgnoreCase(riskLevel);
    }

    /**
     * 风险等级是否需要额外挑战。
     * 当前策略：L3/L4/L5 需要挑战。
     */
    public boolean isChallengeRequired(String riskLevel) {
        if (riskLevel == null) {
            return false;
        }
        String normalized = riskLevel.trim().toUpperCase(Locale.ROOT);
        return "L3".equals(normalized) || "L4".equals(normalized) || "L5".equals(normalized);
    }

    /**
     * 判断给定 token+ip 是否存在 WAF 已验证标记。
     */
    private boolean isWafVerifiedForIp(String token, String ip) {
        if (StrUtil.isBlank(token) || StrUtil.isBlank(ip)) {
            return false;
        }
        String key = wafVerifiedKey(token.trim(), ip.trim());
        Boolean exists = stringRedisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 写入 WAF 已验证标记（短 TTL）。
     */
    private void markWafVerifiedForIp(String token, String ip) {
        if (StrUtil.isBlank(token) || StrUtil.isBlank(ip)) {
            return;
        }
        String key = wafVerifiedKey(token.trim(), ip.trim());
        stringRedisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(Math.max(1, wafVerifiedTtlMinutes)));
    }

    /**
     * 删除 WAF 已验证标记。
     */
    private void clearWafVerifiedForIp(String token, String ip) {
        if (StrUtil.isBlank(token) || StrUtil.isBlank(ip)) {
            return;
        }
        stringRedisTemplate.delete(wafVerifiedKey(token.trim(), ip.trim()));
    }

    /**
     * 生成 WAF 验证标记的 Redis key。
     * 说明：IP 部分做哈希，避免 key 里出现明文 IP。
     */
    private String wafVerifiedKey(String token, String ip) {
        return wafVerifiedKeyPrefix + token + ":" + sha256(ip);
    }

    /**
     * 从 cookie 中读取 preauth token。
     */
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

    /**
     * 判断当前请求是否为 HTTPS。
     * 兼容反向代理场景下的 X-Forwarded-Proto。
     */
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

    /**
     * 规范化 cookie path，确保最终一定是以 "/" 开头的有效路径。
     */
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

    /**
     * 规范化 SameSite 值，只允许 Strict/Lax/None 三种标准写法。
     */
    private String normalizeSameSite(String rawSameSite) {
        String normalized = StrUtil.blankToDefault(rawSameSite, "Lax").trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> "Lax";
        };
    }

    /**
     * 在现有绑定基础上刷新上下文。
     * 若 IP 发生变化，会同步更新最近 IP、变化次数、风险分和风险等级。
     */
    private PreAuthBinding refreshExistingBinding(PreAuthBinding existing, String currentIp) {
        long now = System.currentTimeMillis();
        boolean ipChanged = !StrUtil.equals(existing.currentIp(), currentIp);
        int changeCount = existing.changeCount();
        List<String> recentIps = new ArrayList<>(existing.recentIps());
        String riskLevel = existing.riskLevel();
        int score = existing.score();

        if (ipChanged) {
            // IP 漂移时，把新 IP 放入最近列表首位，并重算风险。
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

    /**
     * 查询并解析 IP 风险信息，返回统一风险快照。
     */
    private RiskProfile resolveRiskProfile(String ip) {
        int score = DEFAULT_SCORE_WHEN_UNAVAILABLE;
        IpReputationMultiLevelQueryService.MultiLevelQueryResult result = ipReputationMultiLevelQueryService.queryEvidence(ip);
        if (result != null && result.success() && result.currentScore() != null) {
            score = result.currentScore();
        }
        String riskLevel = challengePolicy.resolveRiskLevel(score);
        return new RiskProfile(score, riskLevel);
    }

    /**
     * 从 Redis 加载绑定对象。
     * 解析失败时返回 null，交给上层按“过期/不存在”处理。
     */
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

    /**
     * 将绑定对象持久化到 Redis，并刷新 TTL。
     */
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

    /**
     * 删除绑定信息（用于指纹/UA 校验失败后的清理）。
     */
    private void deleteBinding(String token) {
        if (StrUtil.isBlank(token)) {
            return;
        }
        stringRedisTemplate.delete(redisKey(token.trim()));
    }

    /**
     * 构建 preauth 主键。
     */
    private String redisKey(String token) {
        return redisKeyPrefix + token;
    }

    /**
     * 计算统一 TTL，最少 1 分钟，避免出现非正值配置。
     */
    private Duration ttl() {
        return Duration.ofMinutes(Math.max(1, ttlMinutes));
    }

    /**
     * 向最近 IP 列表追加新 IP。
     * 处理策略：
     * 1) 去重（同 IP 先删旧位置）；
     * 2) 头插（最新在前）；
     * 3) 按配置裁剪长度。
     */
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

    /**
     * 将 JSON 字符串解析为最近 IP 列表。
     */
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

    /**
     * 将最近 IP 列表序列化为 JSON 字符串。
     */
    private String stringifyRecentIps(List<String> recentIps) {
        try {
            return objectMapper.writeValueAsString(recentIps == null ? List.of() : recentIps);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    /**
     * 规范化设备指纹。
     * 若前端未传，使用 UA + Accept-Language 组装一个兜底指纹。
     */
    private String normalizeFingerprint(String rawFingerprint, HttpServletRequest request) {
        if (StrUtil.isNotBlank(rawFingerprint)) {
            return rawFingerprint.trim();
        }
        String userAgent = resolveUserAgent(request);
        String language = StrUtil.blankToDefault(request.getHeader("Accept-Language"), "unknown");
        return userAgent + "|" + language;
    }

    /**
     * 读取并规范化 User-Agent。
     */
    private String resolveUserAgent(HttpServletRequest request) {
        return StrUtil.blankToDefault(request.getHeader("User-Agent"), "unknown").trim();
    }

    /**
     * 解析客户端真实 IP。
     * 优先级：X-Forwarded-For 第一个地址 > X-Real-IP > remoteAddr。
     */
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

    /**
     * 计算 SHA-256 十六进制字符串。
     * 出现异常时兜底返回原始值（避免请求直接失败）。
     */
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

    /**
     * 安全解析 int，失败时回退默认值。
     */
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

    /**
     * 安全解析 long，失败时回退默认值。
     */
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

    /**
     * 将对象转字符串，null 转为空串。
     */
    private String toStringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * 将内部绑定对象转换为对外返回的轻量快照对象。
     */
    private PreAuthSnapshot toSnapshot(PreAuthBinding binding) {
        return new PreAuthSnapshot(
                binding.token(),
                binding.riskLevel(),
                isChallengeRequired(binding.riskLevel()),
                isBlockedRisk(binding.riskLevel()),
                binding.expiresAtEpochMillis()
        );
    }

    /**
     * 风险快照（内部使用）。
     */
    private record RiskProfile(int score,
                               String riskLevel) {
    }

    /**
     * 预登录快照（返回给控制器/前端）。
     */
    public record PreAuthSnapshot(String token,
                                  String riskLevel,
                                  boolean challengeRequired,
                                  boolean blocked,
                                  long expiresAtEpochMillis) {
    }

    /**
     * 校验结果包装。
     */
    public record ValidationOutcome(boolean valid,
                                    ValidationError error,
                                    PreAuthBinding binding) {
        /**
         * 构建“校验通过”结果。
         */
        public static ValidationOutcome valid(PreAuthBinding binding) {
            return new ValidationOutcome(true, ValidationError.NONE, binding);
        }

        /**
         * 构建“校验失败”结果。
         */
        public static ValidationOutcome invalid(ValidationError error) {
            return new ValidationOutcome(false, error, null);
        }
    }

    /**
     * 校验失败原因枚举。
     */
    public enum ValidationError {
        /**
         * 无错误（仅用于成功态占位）。
         */
        NONE,
        /**
         * 缺少 token。
         */
        MISSING_TOKEN,
        /**
         * token 过期或不存在。
         */
        EXPIRED,
        /**
         * 设备指纹不匹配。
         */
        FINGERPRINT_MISMATCH,
        /**
         * User-Agent 不匹配。
         */
        USER_AGENT_MISMATCH,
        /**
         * IP 已变化且尚未完成 WAF 验证。
         */
        IP_CHANGED_WAF_REQUIRED
    }

    /**
     * Redis 中保存的预登录绑定对象。
     */
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
