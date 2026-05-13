package com.example.ShoppingSystem.service.user.auth.risk.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.mapper.UserRiskAccountTerminationMapper;
import com.example.ShoppingSystem.mapper.UserRiskProfileMapper;
import com.example.ShoppingSystem.redisdata.UserAuthRiskRedisKeys;
import com.example.ShoppingSystem.service.user.auth.risk.TerminatedAccountEmailBloomService;
import com.example.ShoppingSystem.service.user.auth.risk.UserAuthFailureRiskService;
import com.example.ShoppingSystem.service.user.auth.risk.model.UserAuthFailureType;
import com.example.ShoppingSystem.service.user.auth.risk.model.UserAuthLockStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserAuthFailureRiskServiceImpl implements UserAuthFailureRiskService {

    private static final int SINGLE_FAILURE_LOCK_THRESHOLD = 8;
    private static final int TOTAL_FAILURE_LOCK_THRESHOLD = 15;
    private static final int DEFAULT_ENV_SCORE = 10000;
    private static final int ACCOUNT_SCORE_L6_THRESHOLD = 3000;
    private static final String LOCK_MESSAGE =
            "For security reasons, this account is temporarily unavailable. Please try again later.";
    private static final String REASON_AUTH_FAIL_LOCK = "AUTH_FAIL_LOCK_30M";
    private static final String REASON_TERMINATION_REQUIRED = "ACCOUNT_TERMINATION_REQUIRED";
    private static final String EVENT_AUTH_FAIL_LOCK = "AUTH_FAIL_LOCK_30M";
    private static final String EVENT_TERMINATION_REQUIRED = "ACCOUNT_TERMINATION_REQUIRED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String STATUS_RISK_TERMINATED = "RISK_TERMINATED";

    private final StringRedisTemplate stringRedisTemplate;
    private final UserRiskProfileMapper userRiskProfileMapper;
    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final UserRiskAccountTerminationMapper userRiskAccountTerminationMapper;
    private final TerminatedAccountEmailBloomService terminatedAccountEmailBloomService;
    private final SnowflakeIdWorker snowflakeIdWorker;

    public UserAuthFailureRiskServiceImpl(StringRedisTemplate stringRedisTemplate,
                                          UserRiskProfileMapper userRiskProfileMapper,
                                          UserLoginIdentityMapper userLoginIdentityMapper,
                                          UserRiskAccountTerminationMapper userRiskAccountTerminationMapper,
                                          TerminatedAccountEmailBloomService terminatedAccountEmailBloomService,
                                          SnowflakeIdWorker snowflakeIdWorker) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userRiskProfileMapper = userRiskProfileMapper;
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.userRiskAccountTerminationMapper = userRiskAccountTerminationMapper;
        this.terminatedAccountEmailBloomService = terminatedAccountEmailBloomService;
        this.snowflakeIdWorker = snowflakeIdWorker;
    }

    @Override
    public UserAuthLockStatus checkLock(Long userId) {
        if (userId == null) {
            return UserAuthLockStatus.allowed();
        }
        String lockKey = UserAuthRiskRedisKeys.authLockKey(userId);
        Boolean exists = stringRedisTemplate.hasKey(lockKey);
        if (!Boolean.TRUE.equals(exists)) {
            return UserAuthLockStatus.allowed();
        }
        Long ttlMs = stringRedisTemplate.getExpire(lockKey, TimeUnit.MILLISECONDS);
        return UserAuthLockStatus.builder()
                .blocked(true)
                .terminationRequired(false)
                .retryAfterMs(ttlMs != null && ttlMs > 0L ? ttlMs : null)
                .message(LOCK_MESSAGE)
                .status(STATUS_LOCKED)
                .reason(REASON_AUTH_FAIL_LOCK)
                .build();
    }

    @Override
    @Transactional
    public UserAuthLockStatus checkAccountStatusAndLock(Long userId, String identityStatus) {
        if (userId == null) {
            return UserAuthLockStatus.allowed();
        }
        String status = normalizeStatus(identityStatus);
        if (STATUS_DISABLED.equals(status)) {
            return blockedStatus(STATUS_DISABLED, null, false);
        }
        if (STATUS_RISK_TERMINATED.equals(status)) {
            return blockedStatus(STATUS_RISK_TERMINATED, null, true);
        }
        if (STATUS_LOCKED.equals(status)) {
            UserAuthLockStatus lockedStatus = checkLockedStatus(userId);
            return lockedStatus.isBlocked() ? lockedStatus : checkAccountScore(userId);
        }
        UserAuthLockStatus scoreStatus = checkAccountScore(userId);
        if (scoreStatus.isBlocked()) {
            return scoreStatus;
        }
        return checkLock(userId);
    }

    @Override
    @Transactional
    public UserAuthLockStatus recordFailure(Long userId,
                                            UserAuthFailureType failureType,
                                            String ip,
                                            String deviceFingerprint) {
        if (userId == null || failureType == null) {
            return UserAuthLockStatus.allowed();
        }
        UserAuthLockStatus existingLock = checkLock(userId);
        if (existingLock.isBlocked()) {
            return existingLock;
        }

        long currentFailureCount = incrementFailureCounter(userId, failureType);
        long totalFailureCount = incrementFailureCounter(userId, null);
        FailureWindowSnapshot snapshot = readSnapshot(userId);
        if (currentFailureCount <= SINGLE_FAILURE_LOCK_THRESHOLD
                && totalFailureCount <= TOTAL_FAILURE_LOCK_THRESHOLD) {
            return UserAuthLockStatus.allowed();
        }
        return triggerLock(userId, snapshot, ip, deviceFingerprint);
    }

    private long incrementFailureCounter(Long userId, UserAuthFailureType failureType) {
        String key = failureType == null
                ? UserAuthRiskRedisKeys.failTotal30mKey(userId)
                : failureKey(userId, failureType);
        Long value = stringRedisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            stringRedisTemplate.expire(
                    key,
                    UserAuthRiskRedisKeys.AUTH_FAILURE_WINDOW_MINUTES,
                    TimeUnit.MINUTES
            );
        }
        return value == null ? 0L : value;
    }

    private UserAuthLockStatus triggerLock(Long userId,
                                           FailureWindowSnapshot snapshot,
                                           String ip,
                                           String deviceFingerprint) {
        Map<String, Object> state = userRiskProfileMapper.findUserRiskStateByUserId(userId);
        int previousLockCount = readInteger(state, "lockCount", 0);
        int nextLockCount = previousLockCount + 1;
        LockDecision decision = resolveLockDecision(nextLockCount);
        if (!decision.terminationRequired()) {
            String lockKey = UserAuthRiskRedisKeys.authLockKey(userId);
            Boolean marked = stringRedisTemplate.opsForValue().setIfAbsent(
                    lockKey,
                    "",
                    Math.max(1L, decision.guardSeconds()),
                    TimeUnit.SECONDS
            );
            if (!Boolean.TRUE.equals(marked)) {
                return checkLock(userId);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime lockUntil = decision.lockSeconds() > 0L ? now.plusSeconds(decision.lockSeconds()) : null;
        int scoreBefore = readScoreBefore(state);
        String riskLevelBefore = readString(state, "riskLevel", resolveRiskLevel(scoreBefore));
        int currentEnvScore = readCurrentEnvScore(state, scoreBefore);
        int behaviorScoreDelta = readInteger(state, "behaviorScoreDelta", 0);
        int scoreAfter;
        int nextBehaviorScoreDelta;
        String eventType;
        String lockReason;
        if (decision.terminationRequired()) {
            scoreAfter = 0;
            nextBehaviorScoreDelta = -currentEnvScore;
            eventType = EVENT_TERMINATION_REQUIRED;
            lockReason = REASON_TERMINATION_REQUIRED;
            userLoginIdentityMapper.updateStatusByUserId(userId, STATUS_RISK_TERMINATED);
            upsertRiskTermination(userId, now, lockReason);
        } else {
            nextBehaviorScoreDelta = behaviorScoreDelta - decision.penaltyScore();
            scoreAfter = clampScore(currentEnvScore + nextBehaviorScoreDelta);
            eventType = EVENT_AUTH_FAIL_LOCK;
            lockReason = REASON_AUTH_FAIL_LOCK;
            userLoginIdentityMapper.updateStatusByUserIdIfStatus(userId, STATUS_ACTIVE, STATUS_LOCKED);
        }
        String riskLevelAfter = resolveRiskLevel(scoreAfter);

        userRiskProfileMapper.upsertUserAuthLockState(
                userId,
                currentEnvScore,
                nextBehaviorScoreDelta,
                scoreAfter,
                riskLevelAfter,
                nextLockCount,
                now,
                lockUntil,
                lockReason,
                now
        );
        userRiskProfileMapper.insertUserRiskScoreEvent(
                snowflakeIdWorker.nextId(),
                userId,
                eventType,
                scoreBefore,
                scoreAfter - scoreBefore,
                scoreAfter,
                riskLevelBefore,
                riskLevelAfter,
                lockReason,
                normalizeText(ip),
                normalizeText(deviceFingerprint),
                buildMetadata(snapshot, nextLockCount, decision),
                now
        );
        stringRedisTemplate.delete(failureKeys(userId));

        return UserAuthLockStatus.builder()
                .blocked(true)
                .terminationRequired(decision.terminationRequired())
                .retryAfterMs(!decision.terminationRequired() && decision.lockSeconds() > 0L
                        ? TimeUnit.SECONDS.toMillis(decision.lockSeconds())
                        : null)
                .message(LOCK_MESSAGE)
                .status(decision.terminationRequired() ? STATUS_RISK_TERMINATED : STATUS_LOCKED)
                .reason(lockReason)
                .build();
    }

    private UserAuthLockStatus checkLockedStatus(Long userId) {
        UserAuthLockStatus redisLock = checkLock(userId);
        if (redisLock.isBlocked()) {
            return redisLock;
        }

        Map<String, Object> state = userRiskProfileMapper.findUserRiskStateByUserId(userId);
        OffsetDateTime lockUntil = readOffsetDateTime(state, "lockUntil");
        OffsetDateTime now = OffsetDateTime.now();
        if (lockUntil != null && lockUntil.isAfter(now)) {
            long retryAfterMs = Math.max(1L, Duration.between(now, lockUntil).toMillis());
            stringRedisTemplate.opsForValue().set(
                    UserAuthRiskRedisKeys.authLockKey(userId),
                    "",
                    retryAfterMs,
                    TimeUnit.MILLISECONDS
            );
            return UserAuthLockStatus.builder()
                    .blocked(true)
                    .terminationRequired(false)
                    .retryAfterMs(retryAfterMs)
                    .message(LOCK_MESSAGE)
                    .status(STATUS_LOCKED)
                    .reason(readString(state, "lockReason", REASON_AUTH_FAIL_LOCK))
                    .build();
        }

        int activated = userLoginIdentityMapper.updateStatusByUserIdIfStatus(userId, STATUS_LOCKED, STATUS_ACTIVE);
        if (activated > 0) {
            userRiskProfileMapper.markRiskRecoveryStarted(userId, now);
        }
        return UserAuthLockStatus.allowed();
    }

    private UserAuthLockStatus blockedStatus(String status, Long retryAfterMs, boolean terminationRequired) {
        return UserAuthLockStatus.builder()
                .blocked(true)
                .terminationRequired(terminationRequired)
                .retryAfterMs(retryAfterMs)
                .message(LOCK_MESSAGE)
                .status(status)
                .reason(status)
                .build();
    }

    private UserAuthLockStatus checkAccountScore(Long userId) {
        if (userId == null) {
            return UserAuthLockStatus.allowed();
        }
        Map<String, Object> state = userRiskProfileMapper.findUserRiskStateByUserId(userId);
        if (state == null || state.isEmpty()) {
            return UserAuthLockStatus.allowed();
        }
        int currentScore = readInteger(state, "currentScore", DEFAULT_ENV_SCORE);
        if (currentScore >= ACCOUNT_SCORE_L6_THRESHOLD) {
            return UserAuthLockStatus.allowed();
        }
        return UserAuthLockStatus.builder()
                .blocked(true)
                .terminationRequired(false)
                .retryAfterMs(null)
                .message(MESSAGE_ACCOUNT_SCORE_L6_BLOCKED)
                .status(REASON_ACCOUNT_SCORE_L6_BLOCKED)
                .reason(REASON_ACCOUNT_SCORE_L6_BLOCKED)
                .build();
    }

    private void upsertRiskTermination(Long userId, OffsetDateTime now, String reason) {
        UserLoginIdentity identity = userLoginIdentityMapper.findByUserId(userId);
        if (identity == null) {
            return;
        }
        String email = normalizeEmail(identity.getEmail());
        if (StrUtil.isBlank(email)) {
            return;
        }
        String emailHash = sha256(email);
        String phone = Boolean.TRUE.equals(identity.getPhoneVerified()) ? normalizeText(identity.getPhone()) : null;
        userRiskAccountTerminationMapper.upsertRiskTermination(
                snowflakeIdWorker.nextId(),
                userId,
                email,
                emailHash,
                phone,
                phone == null ? null : sha256(phone),
                reason,
                now,
                now
        );
        terminatedAccountEmailBloomService.addTerminatedEmailHashAsync(emailHash);
    }

    private FailureWindowSnapshot readSnapshot(Long userId) {
        return new FailureWindowSnapshot(
                readLong(UserAuthRiskRedisKeys.pwdFail30mKey(userId)),
                readLong(UserAuthRiskRedisKeys.emailOtpFail30mKey(userId)),
                readLong(UserAuthRiskRedisKeys.smsOtpFail30mKey(userId)),
                readLong(UserAuthRiskRedisKeys.failTotal30mKey(userId))
        );
    }

    private String buildMetadata(FailureWindowSnapshot snapshot, int lockCount, LockDecision decision) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("pwdFail", snapshot.pwdFail());
        metadata.put("emailOtpFail", snapshot.emailOtpFail());
        metadata.put("smsOtpFail", snapshot.smsOtpFail());
        metadata.put("failTotal", snapshot.failTotal());
        metadata.put("lockCount", lockCount);
        metadata.put("lockDays", decision.lockSeconds() <= 0L ? 0L : TimeUnit.SECONDS.toDays(decision.lockSeconds()));
        metadata.put("penaltyScore", decision.penaltyScore());
        metadata.put("terminationRequired", decision.terminationRequired());
        return JSONUtil.toJsonStr(metadata);
    }

    private LockDecision resolveLockDecision(int lockCount) {
        return switch (lockCount) {
            case 1 -> new LockDecision(TimeUnit.DAYS.toSeconds(1), 600, false);
            case 2 -> new LockDecision(TimeUnit.DAYS.toSeconds(3), 1000, false);
            case 3 -> new LockDecision(TimeUnit.DAYS.toSeconds(7), 1500, false);
            default -> new LockDecision(0L, 0, true);
        };
    }

    private String failureKey(Long userId, UserAuthFailureType failureType) {
        return switch (failureType) {
            case PASSWORD -> UserAuthRiskRedisKeys.pwdFail30mKey(userId);
            case EMAIL_OTP -> UserAuthRiskRedisKeys.emailOtpFail30mKey(userId);
            case SMS_OTP -> UserAuthRiskRedisKeys.smsOtpFail30mKey(userId);
        };
    }

    private List<String> failureKeys(Long userId) {
        return List.of(
                UserAuthRiskRedisKeys.pwdFail30mKey(userId),
                UserAuthRiskRedisKeys.emailOtpFail30mKey(userId),
                UserAuthRiskRedisKeys.smsOtpFail30mKey(userId),
                UserAuthRiskRedisKeys.failTotal30mKey(userId)
        );
    }

    private long readLong(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private int readScoreBefore(Map<String, Object> state) {
        int score = readInteger(state, "currentScore", DEFAULT_ENV_SCORE);
        return clampScore(score);
    }

    private int readCurrentEnvScore(Map<String, Object> state, int scoreBefore) {
        int envScore = readInteger(state, "currentEnvScore", 0);
        if (envScore > 0) {
            return clampScore(envScore);
        }
        return scoreBefore > 0 ? scoreBefore : DEFAULT_ENV_SCORE;
    }

    private int readInteger(Map<String, Object> state, String key, int fallback) {
        if (state == null || !state.containsKey(key)) {
            return fallback;
        }
        Object value = state.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || StrUtil.isBlank(value.toString())) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String readString(Map<String, Object> state, String key, String fallback) {
        if (state == null || !state.containsKey(key)) {
            return fallback;
        }
        String value = normalizeText(String.valueOf(state.get(key)));
        return value == null ? fallback : value;
    }

    private OffsetDateTime readOffsetDateTime(Map<String, Object> state, String key) {
        if (state == null || !state.containsKey(key)) {
            return null;
        }
        Object value = state.get(key);
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
        String text = normalizeText(value == null ? null : String.valueOf(value));
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(10000, score));
    }

    private String resolveRiskLevel(int score) {
        int safeScore = clampScore(score);
        if (safeScore >= 8500) {
            return "L1";
        }
        if (safeScore >= 7500) {
            return "L2";
        }
        if (safeScore >= 6000) {
            return "L3";
        }
        if (safeScore >= 4800) {
            return "L4";
        }
        if (safeScore >= 3000) {
            return "L5";
        }
        return "L6";
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeStatus(String status) {
        return StrUtil.blankToDefault(status, STATUS_ACTIVE).trim().toUpperCase();
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeText(email);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(StrUtil.nullToEmpty(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", e);
        }
    }

    private record FailureWindowSnapshot(long pwdFail,
                                         long emailOtpFail,
                                         long smsOtpFail,
                                         long failTotal) {
    }

    private record LockDecision(long lockSeconds, int penaltyScore, boolean terminationRequired) {

        private long guardSeconds() {
            return lockSeconds > 0L ? lockSeconds : TimeUnit.DAYS.toSeconds(1);
        }
    }
}
