package com.example.ShoppingSystem.service.user.auth.register.impl;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.RegisterRiskProfileMapper;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.redisdata.RegisterRedisKeys;
import com.example.ShoppingSystem.service.user.auth.login.UserProfileService;
import com.example.ShoppingSystem.service.user.auth.login.model.UserProfileDraft;
import com.example.ShoppingSystem.service.user.auth.register.RegisterCompletionService;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterCompletionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Completes register flow after email code verification.
 */
@Service
public class RegisterCompletionServiceImpl implements RegisterCompletionService {

    private static final Logger log = LoggerFactory.getLogger(RegisterCompletionServiceImpl.class);
    private static final Pattern EMAIL_CODE_PATTERN = Pattern.compile("^\\d{4,8}$");

    private final StringRedisTemplate stringRedisTemplate;
    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final UserProfileService userProfileService;
    private final RegisterRiskProfileMapper registerRiskProfileMapper;
    private final SnowflakeIdWorker snowflakeIdWorker;

    public RegisterCompletionServiceImpl(StringRedisTemplate stringRedisTemplate,
                                         UserLoginIdentityMapper userLoginIdentityMapper,
                                         UserProfileService userProfileService,
                                         RegisterRiskProfileMapper registerRiskProfileMapper,
                                         SnowflakeIdWorker snowflakeIdWorker) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.userProfileService = userProfileService;
        this.registerRiskProfileMapper = registerRiskProfileMapper;
        this.snowflakeIdWorker = snowflakeIdWorker;
    }

    @Override
    @Transactional
    public RegisterCompletionResult verifyEmailCodeAndRegister(String email,
                                                               String emailCode,
                                                               String deviceFingerprint,
                                                               String requestIp) {
        String normalizedEmail = normalizeEmail(email);
        if (!Validator.isEmail(normalizedEmail)) {
            return fail("Please enter a valid email address.");
        }
        String normalizedEmailCode = normalizeEmailCode(emailCode);
        if (!EMAIL_CODE_PATTERN.matcher(normalizedEmailCode).matches()) {
            return fail("Please enter a valid email code.");
        }

        String codeKey = RegisterRedisKeys.EMAIL_CODE_PREFIX + normalizedEmail;
        String metaKey = RegisterRedisKeys.EMAIL_CODE_META_PREFIX + normalizedEmail;

        String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (StrUtil.isBlank(cachedCode)) {
            return fail("Email code expired, please resend.");
        }
        if (!Objects.equals(normalizedEmailCode, cachedCode)) {
            return fail("Email code is incorrect.");
        }

        String metaJson = stringRedisTemplate.opsForValue().get(metaKey);
        if (StrUtil.isBlank(metaJson)) {
            return fail("Register context expired, please submit register form again.");
        }

        JSONObject meta;
        try {
            meta = JSONUtil.parseObj(metaJson);
        } catch (Exception ex) {
            return fail("Register context invalid, please submit register form again.");
        }

        String metaEmail = normalizeEmail(meta.getStr("email"));
        if (!Objects.equals(normalizedEmail, metaEmail)) {
            return fail("Register context mismatch, please retry.");
        }

        String metaDeviceFingerprint = normalizeText(meta.getStr("deviceFingerprint"));
        String resolvedDeviceFingerprint = resolveDeviceFingerprint(deviceFingerprint, metaDeviceFingerprint);
        if (StrUtil.isBlank(resolvedDeviceFingerprint)) {
            return fail("Device fingerprint is required.");
        }
        if (StrUtil.isNotBlank(metaDeviceFingerprint)
                && StrUtil.isNotBlank(deviceFingerprint)
                && !Objects.equals(normalizeText(deviceFingerprint), metaDeviceFingerprint)) {
            return fail("Device fingerprint mismatch, please restart register.");
        }

        if (userLoginIdentityMapper.findByEmail(normalizedEmail) != null) {
            cleanupRegisterKeys(codeKey, metaKey);
            return fail("Email already registered.");
        }

        String passwordHash = normalizeText(meta.getStr("passwordHash"));
        if (StrUtil.isBlank(passwordHash)) {
            return fail("Register context invalid, please submit register form again.");
        }

        String riskLevelRaw = normalizeRiskLevel(meta.getStr("riskLevel"));
        int totalScore = normalizeScore(parseInteger(meta.get("totalScore")), 0);
        boolean requirePhoneBinding = shouldRequirePhoneBinding(riskLevelRaw);
        String resolvedRequestIp = resolveRequestIp(requestIp, meta.getStr("publicIp"));

        Long userId = snowflakeIdWorker.nextId();
        OffsetDateTime now = OffsetDateTime.now();

        UserLoginIdentity identity = buildEmailIdentity(userId, normalizedEmail, passwordHash, now);
        userLoginIdentityMapper.insertEmailIdentity(identity);

        userProfileService.initIfAbsent(userId, UserProfileDraft.builder()
                .username(resolveUsername(meta.getStr("username"), normalizedEmail))
                .build());

        registerRiskProfileMapper.upsertUserRiskProfile(
                userId,
                totalScore,
                normalizeUserRiskLevel(riskLevelRaw),
                now,
                resolvedRequestIp,
                resolvedDeviceFingerprint,
                now
        );

        registerRiskProfileMapper.upsertDeviceRiskProfile(
                resolvedDeviceFingerprint,
                totalScore,
                normalizeDeviceRiskLevel(riskLevelRaw),
                now,
                now,
                resolvedRequestIp,
                now
        );
        registerRiskProfileMapper.upsertDeviceUserRelation(
                generateRelationId(),
                resolvedDeviceFingerprint,
                userId,
                now
        );
        registerRiskProfileMapper.refreshDeviceLinkedUserCount(resolvedDeviceFingerprint, now);

        cleanupRegisterKeys(codeKey, metaKey);

        return RegisterCompletionResult.builder()
                .success(true)
                .message("Register completed.")
                .userId(userId)
                .requirePhoneBinding(requirePhoneBinding)
                .build();
    }

    private UserLoginIdentity buildEmailIdentity(Long userId,
                                                 String email,
                                                 String passwordHash,
                                                 OffsetDateTime now) {
        return UserLoginIdentity.builder()
                .id(userId)
                .userId(userId)
                .email(email)
                .emailPasswordHash(passwordHash)
                .emailVerified(Boolean.TRUE)
                .phone(null)
                .phoneVerified(Boolean.FALSE)
                .githubId(null)
                .googleId(null)
                .microsoftId(null)
                .tokenVersion(IdUtil.fastSimpleUUID().substring(0, 24))
                .status("ACTIVE")
                .lastLoginAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void cleanupRegisterKeys(String codeKey, String metaKey) {
        try {
            stringRedisTemplate.delete(List.of(codeKey, metaKey));
        } catch (Exception ex) {
            log.warn("Failed to cleanup register keys: codeKey={}, metaKey={}", codeKey, metaKey, ex);
        }
    }

    private RegisterCompletionResult fail(String message) {
        return RegisterCompletionResult.builder()
                .success(false)
                .message(message)
                .requirePhoneBinding(false)
                .build();
    }

    private String resolveDeviceFingerprint(String requestDeviceFingerprint, String metaDeviceFingerprint) {
        String normalizedRequest = normalizeText(requestDeviceFingerprint);
        if (StrUtil.isNotBlank(normalizedRequest)) {
            return normalizedRequest;
        }
        return metaDeviceFingerprint;
    }

    private String resolveRequestIp(String requestIp, String metaIp) {
        String normalizedRequestIp = normalizeText(requestIp);
        if (StrUtil.isNotBlank(normalizedRequestIp)) {
            return normalizedRequestIp;
        }
        return normalizeText(metaIp);
    }

    private String resolveUsername(String username, String normalizedEmail) {
        String normalizedUsername = normalizeText(username);
        if (StrUtil.isNotBlank(normalizedUsername)) {
            return trimToMaxLength(normalizedUsername, 64);
        }
        if (StrUtil.isBlank(normalizedEmail) || !normalizedEmail.contains("@")) {
            return null;
        }
        return trimToMaxLength(normalizedEmail.substring(0, normalizedEmail.indexOf('@')), 64);
    }

    private String normalizeUserRiskLevel(String riskLevel) {
        String normalized = normalizeRiskLevel(riskLevel);
        return switch (normalized) {
            case "L1", "L2", "L3", "L4", "L5" -> normalized;
            default -> "L5";
        };
    }

    private String normalizeDeviceRiskLevel(String riskLevel) {
        String normalized = normalizeRiskLevel(riskLevel);
        return switch (normalized) {
            case "L1", "L2", "L3", "L4", "L5", "L6" -> normalized;
            default -> "L3";
        };
    }

    private boolean shouldRequirePhoneBinding(String riskLevel) {
        String normalized = normalizeRiskLevel(riskLevel);
        return "L3".equals(normalized) || "L4".equals(normalized) || "L5".equals(normalized);
    }

    private byte[] generateRelationId() {
        UUID uuid = UUID.randomUUID();
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    private int normalizeScore(Integer score, int fallback) {
        if (score == null) {
            return fallback;
        }
        return Math.max(0, score);
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    private String normalizeEmailCode(String emailCode) {
        if (emailCode == null) {
            return "";
        }
        return emailCode.trim();
    }

    private String normalizeRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return "";
        }
        return riskLevel.trim().toUpperCase();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String trimToMaxLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
