package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.config.AdminSecurityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class AdminEmailCodeService {

    private static final String EMAIL_CODE_PREFIX = "admin:firstlogin:email-code:";
    private static final String EMAIL_CODE_COOLDOWN_PREFIX = "admin:firstlogin:email-code:cooldown:";

    private final StringRedisTemplate stringRedisTemplate;
    private final AdminSecurityProperties properties;
    private final AdminMailService adminMailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminEmailCodeService(StringRedisTemplate stringRedisTemplate,
                                 AdminSecurityProperties properties,
                                 AdminMailService adminMailService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.adminMailService = adminMailService;
    }

    public void sendFirstLoginEmailCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        String cooldownKey = cooldownKey(normalizedEmail);
        Boolean marked = stringRedisTemplate.opsForValue().setIfAbsent(
                cooldownKey,
                "1",
                Math.max(1, properties.getEmailCodeCooldownSeconds()),
                TimeUnit.SECONDS
        );
        if (!Boolean.TRUE.equals(marked)) {
            Long ttl = stringRedisTemplate.getExpire(cooldownKey, TimeUnit.MILLISECONDS);
            long ttlMs = ttl == null || ttl <= 0L ? 1000L : ttl;
            throw new AdminServiceException(
                    "ADMIN_EMAIL_CODE_RATE_LIMITED",
                    "验证码发送太频繁，请等待 " + Math.max(1L, (ttlMs + 999L) / 1000L) + " 秒后重试。",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        String code = nextSixDigitCode();
        stringRedisTemplate.opsForValue().set(
                codeKey(normalizedEmail),
                code,
                Math.max(1, properties.getEmailCodeTtlMinutes()),
                TimeUnit.MINUTES
        );
        adminMailService.sendFirstLoginEmailCode(
                normalizedEmail,
                code,
                Math.max(1, properties.getEmailCodeTtlMinutes())
        );
    }

    public void verifyAndClear(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = code == null ? "" : code.trim();
        String codeKey = codeKey(normalizedEmail);
        String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (cachedCode == null || cachedCode.isBlank()) {
            throw new AdminServiceException(
                    "ADMIN_EMAIL_CODE_EXPIRED",
                    "邮箱验证码已过期，请重新获取。",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (!cachedCode.equals(normalizedCode)) {
            throw new AdminServiceException(
                    "ADMIN_EMAIL_CODE_INVALID",
                    "邮箱验证码不正确。",
                    HttpStatus.BAD_REQUEST
            );
        }
        stringRedisTemplate.delete(List.of(codeKey, cooldownKey(normalizedEmail)));
    }

    public long ttlSeconds() {
        return TimeUnit.MINUTES.toSeconds(Math.max(1, properties.getEmailCodeTtlMinutes()));
    }

    public long cooldownSeconds() {
        return Math.max(1, properties.getEmailCodeCooldownSeconds());
    }

    private String nextSixDigitCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String codeKey(String email) {
        return EMAIL_CODE_PREFIX + sha256(email);
    }

    private String cooldownKey(String email) {
        return EMAIL_CODE_COOLDOWN_PREFIX + sha256(email);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", ex);
        }
    }
}
