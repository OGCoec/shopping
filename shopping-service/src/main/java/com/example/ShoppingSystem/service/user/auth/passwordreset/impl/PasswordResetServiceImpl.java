package com.example.ShoppingSystem.service.user.auth.passwordreset.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.redisdata.PasswordResetRedisKeys;
import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetMailSender;
import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetService;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetCryptoKey;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetDecryptOutcome;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final String CHALLENGE_WAF_REQUIRED = "WAF_REQUIRED";
    private static final String DEFAULT_RESET_RETURN_PATH = "/shopping/user/forgot-password";
    private static final String RESET_PASSWORD_URL_PATH = "/shopping/user/reset-password-url";
    private static final String RESET_PASSWORD_CODE_PATH = "/shopping/user/reset-password-code";

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetCryptoService passwordResetCryptoService;
    private final PasswordResetMailSender passwordResetMailSender;
    private final ObjectMapper objectMapper;

    public PasswordResetServiceImpl(UserLoginIdentityMapper userLoginIdentityMapper,
                                    StringRedisTemplate stringRedisTemplate,
                                    PasswordEncoder passwordEncoder,
                                    PasswordResetCryptoService passwordResetCryptoService,
                                    PasswordResetMailSender passwordResetMailSender,
                                    ObjectMapper objectMapper) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetCryptoService = passwordResetCryptoService;
        this.passwordResetMailSender = passwordResetMailSender;
        this.objectMapper = objectMapper;
    }

    @Override
    public PasswordResetResult issueCryptoKey() {
        PasswordResetCryptoKey key = passwordResetCryptoService.issueKey();
        return PasswordResetResult.cryptoKey(key);
    }

    @Override
    public PasswordResetResult sendResetLink(String email,
                                             String preAuthToken,
                                             String riskLevel,
                                             boolean wafResumeRequest,
                                             String baseUrl) {
        String normalizedEmail = normalizeEmail(email);
        PasswordResetResult riskResult = requireRiskPass(preAuthToken, riskLevel, wafResumeRequest);
        if (riskResult != null) {
            return riskResult;
        }
        PasswordResetResult cooldownResult = rejectIfCooldown(normalizedEmail);
        if (cooldownResult != null) {
            return cooldownResult;
        }

        UserLoginIdentity identity = findResettableIdentity(normalizedEmail);
        if (identity != null) {
            String token = IdUtil.nanoId(48);
            Map<String, Object> linkPayload = new LinkedHashMap<>();
            linkPayload.put("email", normalizedEmail);
            linkPayload.put("userId", identity.getUserId());
            stringRedisTemplate.opsForValue().set(
                    linkKey(token),
                    JSONUtil.toJsonStr(linkPayload),
                    Duration.ofMinutes(PasswordResetRedisKeys.LINK_TTL_MINUTES));
            passwordResetMailSender.sendResetLink(
                    normalizedEmail,
                    buildResetUrl(baseUrl, token),
                    PasswordResetRedisKeys.LINK_TTL_MINUTES);
        }

        startCooldown(normalizedEmail);
        return PasswordResetResult.okWithRetryAfter(
                "If this email exists, a password reset link has been sent.",
                Duration.ofSeconds(PasswordResetRedisKeys.SEND_COOLDOWN_SECONDS).toMillis());
    }

    @Override
    public PasswordResetResult sendEmailCode(String email,
                                             String preAuthToken,
                                             String riskLevel,
                                             boolean wafResumeRequest,
                                             String baseUrl) {
        String normalizedEmail = normalizeEmail(email);
        PasswordResetResult riskResult = requireRiskPass(preAuthToken, riskLevel, wafResumeRequest);
        if (riskResult != null) {
            return riskResult;
        }
        PasswordResetResult cooldownResult = rejectIfCooldown(normalizedEmail);
        if (cooldownResult != null) {
            return cooldownResult;
        }

        UserLoginIdentity identity = findResettableIdentity(normalizedEmail);
        if (identity != null) {
            String code = RandomUtil.randomNumbers(6);
            String token = IdUtil.nanoId(48);
            stringRedisTemplate.opsForValue().set(
                    emailCodeKey(normalizedEmail),
                    code,
                    Duration.ofMinutes(PasswordResetRedisKeys.EMAIL_CODE_TTL_MINUTES));

            Map<String, Object> linkPayload = new LinkedHashMap<>();
            linkPayload.put("email", normalizedEmail);
            linkPayload.put("userId", identity.getUserId());
            stringRedisTemplate.opsForValue().set(
                    linkKey(token),
                    JSONUtil.toJsonStr(linkPayload),
                    Duration.ofMinutes(PasswordResetRedisKeys.LINK_TTL_MINUTES));

            passwordResetMailSender.sendResetCodeAndLink(
                    normalizedEmail,
                    code,
                    buildResetUrl(baseUrl, token),
                    PasswordResetRedisKeys.EMAIL_CODE_TTL_MINUTES,
                    PasswordResetRedisKeys.LINK_TTL_MINUTES);
        }

        startCooldown(normalizedEmail);
        return PasswordResetResult.okWithRetryAfter(
                "If this email exists, a verification code and reset link have been sent.",
                Duration.ofSeconds(PasswordResetRedisKeys.SEND_COOLDOWN_SECONDS).toMillis());
    }

    @Override
    @Transactional
    public PasswordResetResult resetByLink(String token,
                                           String kid,
                                           String payloadCipher,
                                           String nonce,
                                           Long timestamp) {
        String normalizedToken = normalizeToken(token);
        if (normalizedToken == null) {
            return PasswordResetResult.fail("PASSWORD_RESET_LINK_INVALID", "Password reset link is invalid or expired.");
        }
        String raw = stringRedisTemplate.opsForValue().get(linkKey(normalizedToken));
        if (StrUtil.isBlank(raw)) {
            return PasswordResetResult.fail("PASSWORD_RESET_LINK_INVALID", "Password reset link is invalid or expired.");
        }
        Map<String, Object> linkPayload = parseObject(raw);
        String email = normalizeEmail(asString(linkPayload.get("email")));
        Long userId = parseLong(linkPayload.get("userId"));
        if (StrUtil.isBlank(email) || userId == null) {
            return PasswordResetResult.fail("PASSWORD_RESET_LINK_INVALID", "Password reset link is invalid or expired.");
        }

        PasswordPair passwords = decryptPasswordPair(kid, payloadCipher, nonce, timestamp);
        PasswordResetResult passwordError = validatePasswordPair(passwords);
        if (passwordError != null) {
            return passwordError;
        }

        int updated = updatePassword(userId, passwords.password());
        if (updated <= 0) {
            return PasswordResetResult.fail("PASSWORD_RESET_FAILED", "Password reset failed.");
        }
        stringRedisTemplate.delete(linkKey(normalizedToken));
        stringRedisTemplate.delete(emailCodeKey(email));
        return PasswordResetResult.ok("Password has been reset.");
    }

    @Override
    @Transactional
    public PasswordResetResult verifyEmailCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = normalizeCode(code);
        if (StrUtil.hasBlank(normalizedEmail, normalizedCode)) {
            return PasswordResetResult.fail("PASSWORD_RESET_CODE_INVALID", "Verification code is incorrect or expired.");
        }
        String cachedCode = stringRedisTemplate.opsForValue().get(emailCodeKey(normalizedEmail));
        if (!StrUtil.equals(cachedCode, normalizedCode)) {
            return PasswordResetResult.fail("PASSWORD_RESET_CODE_INVALID", "Verification code is incorrect or expired.");
        }

        UserLoginIdentity identity = findResettableIdentity(normalizedEmail);
        if (identity == null) {
            return PasswordResetResult.fail("PASSWORD_RESET_CODE_INVALID", "Verification code is incorrect or expired.");
        }

        String verifiedToken = IdUtil.nanoId(48);
        stringRedisTemplate.opsForValue().set(
                verifiedKey(verifiedToken),
                normalizedEmail,
                Duration.ofMinutes(PasswordResetRedisKeys.VERIFIED_TTL_MINUTES));
        stringRedisTemplate.delete(emailCodeKey(normalizedEmail));
        return PasswordResetResult.verified("Verification accepted.", RESET_PASSWORD_CODE_PATH + "?token=" + verifiedToken);
    }

    @Override
    @Transactional
    public PasswordResetResult resetByVerifiedCode(String token,
                                                   String kid,
                                                   String payloadCipher,
                                                   String nonce,
                                                   Long timestamp) {
        String normalizedToken = normalizeToken(token);
        if (normalizedToken == null) {
            return PasswordResetResult.fail("PASSWORD_RESET_CODE_TOKEN_INVALID", "Password reset verification is invalid or expired.");
        }
        String email = normalizeEmail(stringRedisTemplate.opsForValue().get(verifiedKey(normalizedToken)));
        if (StrUtil.isBlank(email)) {
            return PasswordResetResult.fail("PASSWORD_RESET_CODE_TOKEN_INVALID", "Password reset verification is invalid or expired.");
        }
        UserLoginIdentity identity = findResettableIdentity(email);
        if (identity == null) {
            return PasswordResetResult.fail("PASSWORD_RESET_CODE_TOKEN_INVALID", "Password reset verification is invalid or expired.");
        }

        PasswordPair passwords = decryptPasswordPair(kid, payloadCipher, nonce, timestamp);
        PasswordResetResult passwordError = validatePasswordPair(passwords);
        if (passwordError != null) {
            return passwordError;
        }

        int updated = updatePassword(identity.getUserId(), passwords.password());
        if (updated <= 0) {
            return PasswordResetResult.fail("PASSWORD_RESET_FAILED", "Password reset failed.");
        }
        stringRedisTemplate.delete(verifiedKey(normalizedToken));
        stringRedisTemplate.delete(emailCodeKey(email));
        return PasswordResetResult.ok("Password has been reset.");
    }

    @Override
    public void markWafVerified(String preAuthToken) {
        String normalizedToken = normalizeToken(preAuthToken);
        if (normalizedToken == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                PasswordResetRedisKeys.WAF_VERIFIED_PREFIX + sha256(normalizedToken),
                "1",
                Duration.ofMinutes(PasswordResetRedisKeys.WAF_VERIFIED_TTL_MINUTES));
    }

    @Override
    public boolean consumeWafVerified(String preAuthToken) {
        String normalizedToken = normalizeToken(preAuthToken);
        if (normalizedToken == null) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.delete(
                PasswordResetRedisKeys.WAF_VERIFIED_PREFIX + sha256(normalizedToken)));
    }

    @Override
    public boolean isResetLinkTokenUsable(String token) {
        String normalizedToken = normalizeToken(token);
        return normalizedToken != null
                && Boolean.TRUE.equals(stringRedisTemplate.hasKey(linkKey(normalizedToken)));
    }

    @Override
    public boolean isVerifiedCodeTokenUsable(String token) {
        String normalizedToken = normalizeToken(token);
        return normalizedToken != null
                && Boolean.TRUE.equals(stringRedisTemplate.hasKey(verifiedKey(normalizedToken)));
    }

    private PasswordResetResult requireRiskPass(String preAuthToken, String riskLevel, boolean wafResumeRequest) {
        String normalizedRiskLevel = normalizeRiskLevel(riskLevel);
        if ("L6".equals(normalizedRiskLevel)) {
            return PasswordResetResult.blocked(normalizedRiskLevel, "Current network risk is too high. Password reset is blocked.");
        }
        if ("L5".equals(normalizedRiskLevel)
                && !(wafResumeRequest && consumeWafVerified(preAuthToken))) {
            return PasswordResetResult.wafRequired(normalizedRiskLevel, buildWafVerifyUrl());
        }
        return null;
    }

    private PasswordResetResult rejectIfCooldown(String email) {
        if (StrUtil.isBlank(email)) {
            return PasswordResetResult.fail("PASSWORD_RESET_EMAIL_INVALID", "Please enter a valid email address.");
        }
        Long ttlSeconds = stringRedisTemplate.getExpire(cooldownKey(email));
        if (ttlSeconds != null && ttlSeconds > 0) {
            return PasswordResetResult.rateLimited(Duration.ofSeconds(ttlSeconds).toMillis());
        }
        return null;
    }

    private void startCooldown(String email) {
        stringRedisTemplate.opsForValue().set(
                cooldownKey(email),
                "1",
                Duration.ofSeconds(PasswordResetRedisKeys.SEND_COOLDOWN_SECONDS));
    }

    private UserLoginIdentity findResettableIdentity(String email) {
        if (StrUtil.isBlank(email)) {
            return null;
        }
        UserLoginIdentity identity = userLoginIdentityMapper.findByEmail(email);
        if (identity == null || !isActive(identity) || StrUtil.isBlank(identity.getEmailPasswordHash())) {
            return null;
        }
        return identity;
    }

    private boolean isActive(UserLoginIdentity identity) {
        return identity != null && "ACTIVE".equalsIgnoreCase(StrUtil.blankToDefault(identity.getStatus(), ""));
    }

    private PasswordPair decryptPasswordPair(String kid, String payloadCipher, String nonce, Long timestamp) {
        PasswordResetDecryptOutcome outcome = passwordResetCryptoService.decryptPayload(kid, payloadCipher, nonce, timestamp);
        if (!outcome.success()) {
            return new PasswordPair("", "", outcome.message());
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(outcome.payload(), new TypeReference<>() {
            });
            return new PasswordPair(
                    asString(payload.get("password")),
                    asString(payload.get("confirmPassword")),
                    "");
        } catch (Exception ignored) {
            return new PasswordPair("", "", "Encrypted password payload is invalid.");
        }
    }

    private PasswordResetResult validatePasswordPair(PasswordPair passwords) {
        if (passwords == null || StrUtil.isNotBlank(passwords.error())) {
            return PasswordResetResult.fail("PASSWORD_RESET_CRYPTO_INVALID",
                    passwords == null ? "Encrypted password payload is invalid." : passwords.error());
        }
        if (StrUtil.isBlank(passwords.password()) || passwords.password().length() < 8) {
            return PasswordResetResult.fail("PASSWORD_RESET_PASSWORD_INVALID", "Password must be at least 8 characters.");
        }
        if (!StrUtil.equals(passwords.password(), passwords.confirmPassword())) {
            return PasswordResetResult.fail("PASSWORD_RESET_PASSWORD_MISMATCH", "Passwords do not match.");
        }
        return null;
    }

    private int updatePassword(Long userId, String rawPassword) {
        return userLoginIdentityMapper.updateEmailPasswordHashByUserId(
                userId,
                passwordEncoder.encode(rawPassword),
                IdUtil.fastSimpleUUID().substring(0, 24));
    }

    private String buildResetUrl(String baseUrl, String token) {
        String normalizedBaseUrl = StrUtil.blankToDefault(baseUrl, "").trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl + RESET_PASSWORD_URL_PATH + "?token=" + token;
    }

    private String buildWafVerifyUrl() {
        return "/shopping/auth/waf/verify?return=%2Fshopping%2Fuser%2Fforgot-password";
    }

    private String linkKey(String token) {
        return PasswordResetRedisKeys.LINK_PREFIX + sha256(token);
    }

    private String verifiedKey(String token) {
        return PasswordResetRedisKeys.VERIFIED_PREFIX + token;
    }

    private String emailCodeKey(String email) {
        return PasswordResetRedisKeys.EMAIL_CODE_PREFIX + sha256(email);
    }

    private String cooldownKey(String email) {
        return PasswordResetRedisKeys.SEND_COOLDOWN_PREFIX + sha256(email);
    }

    private Map<String, Object> parseObject(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(asString(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeEmail(String email) {
        return StrUtil.blankToDefault(email, "").trim();
    }

    private String normalizeCode(String code) {
        String normalized = StrUtil.blankToDefault(code, "").trim();
        return normalized.matches("\\d{6}") ? normalized : "";
    }

    private String normalizeToken(String token) {
        String normalized = StrUtil.blankToDefault(token, "").trim();
        if (normalized.length() != 48) {
            return null;
        }
        for (int index = 0; index < normalized.length(); index += 1) {
            char current = normalized.charAt(index);
            boolean accepted = (current >= 'a' && current <= 'z')
                    || (current >= 'A' && current <= 'Z')
                    || (current >= '0' && current <= '9')
                    || current == '-' || current == '_';
            if (!accepted) {
                return null;
            }
        }
        return normalized;
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = StrUtil.blankToDefault(riskLevel, "L1").trim().toUpperCase();
        return switch (normalized) {
            case "L1", "L2", "L3", "L4", "L5", "L6" -> normalized;
            default -> "L1";
        };
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(StrUtil.nullToEmpty(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }

    private record PasswordPair(String password, String confirmPassword, String error) {
    }
}
