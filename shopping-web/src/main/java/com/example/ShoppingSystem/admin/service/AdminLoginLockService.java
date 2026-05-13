package com.example.ShoppingSystem.admin.service;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.admin.config.AdminSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminLoginLockService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String LOCK_REASON = "PASSWORD_FAILED_TOO_MANY_TIMES";

    private static final DefaultRedisScript<List> CHECK_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
                    local lockKey = KEYS[1]
                    local fallbackLockMs = tonumber(ARGV[1]) or 1800000

                    if redis.call('EXISTS', lockKey) == 1 then
                        local ttl = redis.call('PTTL', lockKey)
                        if ttl < 0 then
                            redis.call('PEXPIRE', lockKey, fallbackLockMs)
                            ttl = fallbackLockMs
                        end
                        return { 'LOCKED', ttl }
                    end

                    return { 'OPEN', 0 }
                    """,
            List.class
    );

    private static final DefaultRedisScript<List> RECORD_FAILURE_SCRIPT = new DefaultRedisScript<>(
            """
                    local failKey = KEYS[1]
                    local lockKey = KEYS[2]

                    local maxFailures = tonumber(ARGV[1]) or 5
                    local failureWindowMs = tonumber(ARGV[2]) or 600000
                    local lockMs = tonumber(ARGV[3]) or 1800000
                    local lockPayload = ARGV[4]

                    if redis.call('EXISTS', lockKey) == 1 then
                        local ttl = redis.call('PTTL', lockKey)
                        if ttl < 0 then
                            redis.call('PEXPIRE', lockKey, lockMs)
                            ttl = lockMs
                        end
                        return { 'LOCKED', ttl, maxFailures }
                    end

                    local count = redis.call('INCR', failKey)
                    if count == 1 then
                        redis.call('PEXPIRE', failKey, failureWindowMs)
                    end

                    if count >= maxFailures then
                        redis.call('SET', lockKey, lockPayload, 'PX', lockMs)
                        redis.call('DEL', failKey)
                        return { 'LOCKED', lockMs, count }
                    end

                    return { 'FAILED', count, redis.call('PTTL', failKey) }
                    """,
            List.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final AdminSecurityProperties properties;
    private final ObjectMapper objectMapper;

    public AdminLoginLockService(StringRedisTemplate stringRedisTemplate,
                                 AdminSecurityProperties properties,
                                 ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public LockStatus checkLocked(String identifier) {
        if (!properties.isLoginLockEnabled()) {
            return LockStatus.open();
        }
        String identifierHash = identifierHash(identifier);
        if (StrUtil.isBlank(identifierHash)) {
            return LockStatus.open();
        }
        List result = stringRedisTemplate.execute(
                CHECK_LOCK_SCRIPT,
                List.of(lockKey(identifierHash)),
                String.valueOf(lockMillis())
        );
        if (result == null || result.isEmpty() || !STATUS_LOCKED.equals(asString(result.get(0)))) {
            return LockStatus.open();
        }
        return LockStatus.locked(positiveLong(result.size() > 1 ? result.get(1) : null, lockMillis()));
    }

    public FailureStatus recordFailure(String identifier, HttpServletRequest request) {
        if (!properties.isLoginLockEnabled()) {
            return FailureStatus.failed(0, 0L);
        }
        String identifierHash = identifierHash(identifier);
        if (StrUtil.isBlank(identifierHash)) {
            return FailureStatus.failed(0, 0L);
        }
        List result = stringRedisTemplate.execute(
                RECORD_FAILURE_SCRIPT,
                List.of(failKey(identifierHash), lockKey(identifierHash)),
                String.valueOf(maxFailures()),
                String.valueOf(failureWindowMillis()),
                String.valueOf(lockMillis()),
                buildLockPayload(identifierHash, request)
        );
        if (result == null || result.isEmpty()) {
            return FailureStatus.failed(0, 0L);
        }
        String status = asString(result.get(0));
        if (STATUS_LOCKED.equals(status)) {
            return FailureStatus.locked(
                    positiveLong(result.size() > 1 ? result.get(1) : null, lockMillis()),
                    intValue(result.size() > 2 ? result.get(2) : null, maxFailures())
            );
        }
        if (STATUS_FAILED.equals(status)) {
            return FailureStatus.failed(
                    intValue(result.size() > 1 ? result.get(1) : null, 0),
                    positiveLong(result.size() > 2 ? result.get(2) : null, failureWindowMillis())
            );
        }
        return FailureStatus.failed(0, 0L);
    }

    public void clearFailures(String identifier) {
        if (!properties.isLoginLockEnabled()) {
            return;
        }
        String identifierHash = identifierHash(identifier);
        if (StrUtil.isBlank(identifierHash)) {
            return;
        }
        stringRedisTemplate.delete(failKey(identifierHash));
    }

    private String buildLockPayload(String identifierHash, HttpServletRequest request) {
        long now = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lockedAt", now);
        payload.put("unlockAt", now + lockMillis());
        payload.put("reason", LOCK_REASON);
        payload.put("failCount", maxFailures());
        payload.put("identifierHash", identifierHash);
        payload.put("lastIpHash", hmacOrBlank(resolveClientIp(request)));
        payload.put("lastUserAgentHash", hmacOrBlank(request == null ? "" : request.getHeader("User-Agent")));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{\"reason\":\"" + LOCK_REASON + "\"}";
        }
    }

    private String failKey(String identifierHash) {
        return "admin:auth:login:{" + identifierHash + "}:fail";
    }

    private String lockKey(String identifierHash) {
        return "admin:auth:login:{" + identifierHash + "}:lock";
    }

    private String identifierHash(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        if (StrUtil.isBlank(normalized)) {
            return "";
        }
        return hmac(normalized);
    }

    private String normalizeIdentifier(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(realIp)) {
            return realIp.trim();
        }
        return StrUtil.blankToDefault(request.getRemoteAddr(), "");
    }

    private String hmacOrBlank(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        return hmac(value.trim());
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(resolveSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("admin login lock HMAC is unavailable", ex);
        }
    }

    private String resolveSecret() {
        return StrUtil.blankToDefault(
                properties.getLoginLockKeySecret(),
                "shopping-admin-login-lock-dev-secret"
        );
    }

    private int maxFailures() {
        return Math.max(1, properties.getLoginLockMaxFailures());
    }

    private long failureWindowMillis() {
        return Math.max(1L, properties.getLoginLockFailureWindowSeconds()) * 1000L;
    }

    private long lockMillis() {
        return Math.max(1L, properties.getLoginLockSeconds()) * 1000L;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long positiveLong(Object value, long fallback) {
        long parsed = longValue(value, fallback);
        return parsed > 0L ? parsed : fallback;
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(asString(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(asString(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public record LockStatus(boolean locked, long retryAfterMs) {
        public static LockStatus open() {
            return new LockStatus(false, 0L);
        }

        public static LockStatus locked(long retryAfterMs) {
            return new LockStatus(true, Math.max(1L, retryAfterMs));
        }
    }

    public record FailureStatus(boolean locked,
                                long retryAfterMs,
                                int failureCount,
                                long failureWindowMs) {
        public static FailureStatus locked(long retryAfterMs, int failureCount) {
            return new FailureStatus(true, Math.max(1L, retryAfterMs), Math.max(0, failureCount), 0L);
        }

        public static FailureStatus failed(int failureCount, long failureWindowMs) {
            return new FailureStatus(false, 0L, Math.max(0, failureCount), Math.max(0L, failureWindowMs));
        }
    }
}
