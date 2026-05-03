package com.example.ShoppingSystem.service.user.auth.login.impl;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.redisdata.LoginRedisKeys;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_OPERATION_TIMEOUT;

@Service
public class LoginChallengeSessionService {

    private static final String CHALLENGE_VALUE_SEPARATOR = "|";

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${login.operation-timeout.wait-min-seconds:5}")
    private int operationTimeoutWaitMinSeconds;

    @Value("${login.operation-timeout.wait-max-seconds:10}")
    private int operationTimeoutWaitMaxSeconds;

    public LoginChallengeSessionService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public ChallengeSelection readPendingChallengeSelection(String email, String deviceFingerprint) {
        if (StrUtil.hasBlank(email, deviceFingerprint)) {
            return null;
        }
        return parseChallengeSelectionValue(stringRedisTemplate.opsForValue().get(pendingChallengeKey(email, deviceFingerprint)));
    }

    public ChallengeSelection savePendingChallengeSelection(String email,
                                                            String deviceFingerprint,
                                                            ChallengeSelection challengeSelection) {
        if (StrUtil.hasBlank(email, deviceFingerprint)
                || challengeSelection == null
                || StrUtil.isBlank(challengeSelection.type())) {
            return challengeSelection;
        }
        String pendingKey = pendingChallengeKey(email, deviceFingerprint);
        String value = challengeSelection.type() + CHALLENGE_VALUE_SEPARATOR + StrUtil.nullToEmpty(challengeSelection.subType());
        stringRedisTemplate.opsForValue().set(
                pendingKey,
                value,
                LoginRedisKeys.CHALLENGE_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        if (isOperationTimeoutChallenge(challengeSelection.type())) {
            ensureOperationTimeoutWaitUntil(email, deviceFingerprint);
        } else {
            clearOperationTimeoutWaitUntil(email, deviceFingerprint);
        }
        return challengeSelection;
    }

    public void clearPendingChallengeSelection(String email, String deviceFingerprint) {
        if (StrUtil.hasBlank(email, deviceFingerprint)) {
            return;
        }
        String pendingKey = pendingChallengeKey(email, deviceFingerprint);
        stringRedisTemplate.delete(pendingKey);
        stringRedisTemplate.delete(operationTimeoutWaitUntilKey(pendingKey));
    }

    public boolean refreshPendingChallengeSelection(String email,
                                                    String deviceFingerprint,
                                                    ChallengeSelection expectedChallengeSelection) {
        if (StrUtil.hasBlank(email, deviceFingerprint)
                || expectedChallengeSelection == null
                || StrUtil.isBlank(expectedChallengeSelection.type())) {
            return false;
        }

        String pendingKey = pendingChallengeKey(email, deviceFingerprint);
        ChallengeSelection currentSelection = parseChallengeSelectionValue(stringRedisTemplate.opsForValue().get(pendingKey));
        if (currentSelection == null) {
            savePendingChallengeSelection(email, deviceFingerprint, expectedChallengeSelection);
            return true;
        }
        if (!isSameChallengeSelection(currentSelection, expectedChallengeSelection)) {
            return false;
        }
        Boolean refreshed = stringRedisTemplate.expire(
                pendingKey,
                LoginRedisKeys.CHALLENGE_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        if (isOperationTimeoutChallenge(expectedChallengeSelection.type())) {
            refreshOperationTimeoutWaitUntilTtl(email, deviceFingerprint);
        }
        return !Boolean.FALSE.equals(refreshed);
    }

    public long ensureOperationTimeoutWaitUntil(String email, String deviceFingerprint) {
        if (StrUtil.hasBlank(email, deviceFingerprint)) {
            return System.currentTimeMillis();
        }
        String waitKey = operationTimeoutWaitUntilKey(pendingChallengeKey(email, deviceFingerprint));
        long now = System.currentTimeMillis();
        Long currentWaitUntil = parseLongValue(stringRedisTemplate.opsForValue().get(waitKey));
        if (currentWaitUntil != null && currentWaitUntil > now) {
            stringRedisTemplate.expire(waitKey, LoginRedisKeys.CHALLENGE_TTL_MINUTES, TimeUnit.MINUTES);
            return currentWaitUntil;
        }

        long nextWaitUntil = now + randomOperationTimeoutWaitMillis();
        stringRedisTemplate.opsForValue().set(
                waitKey,
                String.valueOf(nextWaitUntil),
                LoginRedisKeys.CHALLENGE_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        return nextWaitUntil;
    }

    public Long readOperationTimeoutWaitUntil(String email, String deviceFingerprint) {
        if (StrUtil.hasBlank(email, deviceFingerprint)) {
            return null;
        }
        return parseLongValue(stringRedisTemplate.opsForValue().get(
                operationTimeoutWaitUntilKey(pendingChallengeKey(email, deviceFingerprint))
        ));
    }

    public void markWafVerified(String preAuthToken) {
        String normalizedToken = normalizeText(preAuthToken);
        if (normalizedToken == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                LoginRedisKeys.WAF_VERIFIED_PREFIX + sha256(normalizedToken),
                "1",
                LoginRedisKeys.WAF_VERIFIED_TTL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    public boolean isWafVerified(String preAuthToken) {
        String normalizedToken = normalizeText(preAuthToken);
        if (normalizedToken == null) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(LoginRedisKeys.WAF_VERIFIED_PREFIX + sha256(normalizedToken)));
    }

    private void refreshOperationTimeoutWaitUntilTtl(String email, String deviceFingerprint) {
        String waitKey = operationTimeoutWaitUntilKey(pendingChallengeKey(email, deviceFingerprint));
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(waitKey))) {
            stringRedisTemplate.expire(waitKey, LoginRedisKeys.CHALLENGE_TTL_MINUTES, TimeUnit.MINUTES);
            return;
        }
        ensureOperationTimeoutWaitUntil(email, deviceFingerprint);
    }

    private void clearOperationTimeoutWaitUntil(String email, String deviceFingerprint) {
        stringRedisTemplate.delete(operationTimeoutWaitUntilKey(pendingChallengeKey(email, deviceFingerprint)));
    }

    private boolean isSameChallengeSelection(ChallengeSelection left, ChallengeSelection right) {
        return StrUtil.equals(left.type(), right.type())
                && StrUtil.equals(StrUtil.nullToEmpty(left.subType()), StrUtil.nullToEmpty(right.subType()));
    }

    private boolean isOperationTimeoutChallenge(String challengeType) {
        return CHALLENGE_OPERATION_TIMEOUT.equalsIgnoreCase(StrUtil.blankToDefault(challengeType, ""));
    }

    private String pendingChallengeKey(String email, String deviceFingerprint) {
        return LoginRedisKeys.CHALLENGE_PREFIX + sha256(normalizeEmail(email) + "|" + normalizeText(deviceFingerprint));
    }

    private String operationTimeoutWaitUntilKey(String pendingKey) {
        return pendingKey + LoginRedisKeys.CHALLENGE_OPERATION_TIMEOUT_WAIT_UNTIL_SUFFIX;
    }

    private long randomOperationTimeoutWaitMillis() {
        int minSeconds = Math.max(1, operationTimeoutWaitMinSeconds);
        int maxSeconds = Math.max(minSeconds, operationTimeoutWaitMaxSeconds);
        int randomSeconds = ThreadLocalRandom.current().nextInt(minSeconds, maxSeconds + 1);
        return TimeUnit.SECONDS.toMillis(randomSeconds);
    }

    private ChallengeSelection parseChallengeSelectionValue(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String[] parts = value.split("\\|", 2);
        String type = StrUtil.trimToNull(parts[0]);
        if (type == null) {
            return null;
        }
        String subType = parts.length > 1 && StrUtil.isNotBlank(parts[1]) ? parts[1] : null;
        return new ChallengeSelection(type, subType);
    }

    private Long parseLongValue(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(StrUtil.nullToEmpty(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }

    private String normalizeEmail(String email) {
        return StrUtil.blankToDefault(email, "").trim().toLowerCase();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
