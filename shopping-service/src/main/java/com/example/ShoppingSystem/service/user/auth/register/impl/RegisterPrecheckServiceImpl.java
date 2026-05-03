package com.example.ShoppingSystem.service.user.auth.register.impl;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.redisdata.RegisterRedisKeys;
import com.example.ShoppingSystem.service.user.auth.register.RegisterPrecheckService;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterPrecheckResult;
import com.example.ShoppingSystem.service.user.auth.register.model.RiskSnapshot;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Register precheck orchestration service.
 */
@Service
public class RegisterPrecheckServiceImpl implements RegisterPrecheckService {

    // Compatibility aliases used by tests and legacy callers.
    static final String CHALLENGE_HUTOOL_SHEAR = RegisterChallengeConstants.CHALLENGE_HUTOOL_SHEAR;
    static final String CHALLENGE_TIANAI = RegisterChallengeConstants.CHALLENGE_TIANAI;
    static final String CHALLENGE_CLOUDFLARE_TURNSTILE = RegisterChallengeConstants.CHALLENGE_CLOUDFLARE_TURNSTILE;
    static final String CHALLENGE_HCAPTCHA = RegisterChallengeConstants.CHALLENGE_HCAPTCHA;
    static final String CHALLENGE_OPERATION_TIMEOUT = RegisterChallengeConstants.CHALLENGE_OPERATION_TIMEOUT;

    static final String SUBTYPE_TIANAI_SLIDER = RegisterChallengeConstants.SUBTYPE_TIANAI_SLIDER;
    static final String SUBTYPE_TIANAI_ROTATE = RegisterChallengeConstants.SUBTYPE_TIANAI_ROTATE;
    static final String SUBTYPE_TIANAI_CONCAT = RegisterChallengeConstants.SUBTYPE_TIANAI_CONCAT;
    static final String SUBTYPE_TIANAI_WORD_IMAGE_CLICK = RegisterChallengeConstants.SUBTYPE_TIANAI_WORD_IMAGE_CLICK;
    private static final long EMAIL_CODE_RESEND_COOLDOWN_MS = 60_000L;

    private final ChallengeSessionService challengeSessionService;
    private final CaptchaVerificationService captchaVerificationService;
    private final EmailCodeDispatchService emailCodeDispatchService;
    private final RiskSnapshotService riskSnapshotService;
    private final ChallengePolicy challengePolicy;
    private final StringRedisTemplate stringRedisTemplate;

    public RegisterPrecheckServiceImpl(ChallengeSessionService challengeSessionService,
                                       CaptchaVerificationService captchaVerificationService,
                                       EmailCodeDispatchService emailCodeDispatchService,
                                       RiskSnapshotService riskSnapshotService,
                                       ChallengePolicy challengePolicy,
                                       StringRedisTemplate stringRedisTemplate) {
        this.challengeSessionService = challengeSessionService;
        this.captchaVerificationService = captchaVerificationService;
        this.emailCodeDispatchService = emailCodeDispatchService;
        this.riskSnapshotService = riskSnapshotService;
        this.challengePolicy = challengePolicy;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public RegisterPrecheckResult resolveRegisterEmailCodeChallenge(String email,
                                                                    String username,
                                                                    String rawPassword,
                                                                    String deviceFingerprint,
                                                                    String publicIp) {
        RegisterPrecheckResult validationFailure = validateRegisterInput(email, username, rawPassword, deviceFingerprint);
        if (validationFailure != null) {
            return validationFailure;
        }

        ChallengeSelection pendingChallengeSelection = normalizePendingChallengeSelection(
                email,
                deviceFingerprint,
                challengeSessionService.readPendingChallengeSelection(email, deviceFingerprint));

        RiskSnapshot riskSnapshot = riskSnapshotService.buildRiskSnapshot(publicIp, pendingChallengeSelection);
        ChallengeSelection challengeSelection = riskSnapshot.challengeSelection();
        if (challengeSelection.type() != null) {
            challengeSessionService.savePendingChallengeSelection(email, deviceFingerprint, challengeSelection);
            return buildCaptchaRequiredResult(
                    riskSnapshot.totalScore(),
                    riskSnapshot.riskLevel(),
                    challengeSelection.type(),
                    challengeSelection.subType(),
                    email,
                    deviceFingerprint);
        }

        challengeSessionService.clearPendingChallengeSelection(email, deviceFingerprint);
        return RegisterPrecheckResult.builder()
                .success(true)
                .message("当前无需验证码，可继续发送邮箱验证码")
                .totalScore(riskSnapshot.totalScore())
                .riskLevel(riskSnapshot.riskLevel())
                .emailCodeSent(false)
                .requirePhoneBinding(shouldRequirePhoneBinding(riskSnapshot.riskLevel()))
                .build();
    }

    @Override
    public RegisterPrecheckResult sendRegisterEmailCodeAfterCaptcha(String flowId,
                                                                    boolean allowPassedChallengeReuse,
                                                                    String email,
                                                                    String username,
                                                                    String rawPassword,
                                                                    String deviceFingerprint,
                                                                    String publicIp,
                                                                    String captchaUuid,
                                                                    String captchaCode) {
        RegisterPrecheckResult validationFailure = validateRegisterInput(email, username, rawPassword, deviceFingerprint);
        if (validationFailure != null) {
            return validationFailure;
        }

        ChallengeSelection pendingChallengeSelection = normalizePendingChallengeSelection(
                email,
                deviceFingerprint,
                challengeSessionService.readPendingChallengeSelection(email, deviceFingerprint));

        RiskSnapshot riskSnapshot = riskSnapshotService.buildRiskSnapshot(publicIp, pendingChallengeSelection);
        ChallengeSelection challengeSelection = challengeSessionService.resolveChallengeSelectionForCurrentAttempt(
                pendingChallengeSelection,
                riskSnapshot.challengeSelection());
        String challengeType = challengeSelection.type();
        String challengeSubType = challengeSelection.subType();
        ChallengePassedState challengePassedState = allowPassedChallengeReuse
                ? readChallengePassed(flowId, email, deviceFingerprint)
                : ChallengePassedState.missing();
        boolean canReusePassedChallenge = challengePassedState.matched();

        if (canReusePassedChallenge) {
            RegisterPrecheckResult cooldownFailure = buildEmailCodeCooldownResultIfNecessary(
                    riskSnapshot,
                    challengePassedState.passedAt());
            if (cooldownFailure != null) {
                return cooldownFailure;
            }
        }

        if (challengeType != null && !canReusePassedChallenge) {
            boolean hasCaptchaPayload = StrUtil.isNotBlank(captchaCode) || StrUtil.isNotBlank(captchaUuid);
            if (pendingChallengeSelection == null) {
                challengeSessionService.savePendingChallengeSelection(email, deviceFingerprint, challengeSelection);
                return buildCaptchaRequiredResult(
                        riskSnapshot.totalScore(),
                        riskSnapshot.riskLevel(),
                        challengeType,
                        challengeSubType,
                        email,
                        deviceFingerprint);
            }
            if (!hasCaptchaPayload) {
                challengeSessionService.savePendingChallengeSelection(email, deviceFingerprint, challengeSelection);
                return buildCaptchaRequiredResult(
                        riskSnapshot.totalScore(),
                        riskSnapshot.riskLevel(),
                        challengeType,
                        challengeSubType,
                        email,
                        deviceFingerprint);
            }

            boolean captchaVerified = verifyRequiredCaptcha(
                    challengeType,
                    publicIp,
                    captchaUuid,
                    captchaCode);
            if (!captchaVerified) {
                challengeSessionService.savePendingChallengeSelection(email, deviceFingerprint, challengeSelection);
                return buildCaptchaRequiredResult(
                        riskSnapshot.totalScore(),
                        riskSnapshot.riskLevel(),
                        challengeType,
                        challengeSubType,
                        email,
                        deviceFingerprint);
            }

            challengeSessionService.clearPendingChallengeSelection(email, deviceFingerprint);
        } else {
            challengeSessionService.clearPendingChallengeSelection(email, deviceFingerprint);
        }

        emailCodeDispatchService.dispatchRegisterEmailCode(
                email,
                username,
                rawPassword,
                deviceFingerprint,
                publicIp,
                riskSnapshot,
                challengeSelection);

        long passedAt = System.currentTimeMillis() + EMAIL_CODE_RESEND_COOLDOWN_MS;
        saveChallengePassed(flowId, email, deviceFingerprint, passedAt);

        return RegisterPrecheckResult.builder()
                .success(true)
                .message("邮箱验证码已发送，请在 5 分钟内完成验证")
                .totalScore(riskSnapshot.totalScore())
                .riskLevel(riskSnapshot.riskLevel())
                .challengeType(challengeType)
                .challengeSubType(challengeSubType)
                .emailCodeRetryAfterMs(EMAIL_CODE_RESEND_COOLDOWN_MS)
                .passedAt(passedAt)
                .emailCodeSent(true)
                .requirePhoneBinding(shouldRequirePhoneBinding(riskSnapshot.riskLevel()))
                .build();
    }

    @Override
    public boolean refreshPendingChallengeSelection(String email,
                                                    String deviceFingerprint,
                                                    String expectedChallengeType) {
        return refreshPendingChallengeSelection(email, deviceFingerprint, expectedChallengeType, null);
    }

    @Override
    public boolean refreshPendingChallengeSelection(String email,
                                                    String deviceFingerprint,
                                                    String expectedChallengeType,
                                                    String expectedChallengeSubType) {
        if (StrUtil.hasBlank(email, deviceFingerprint, expectedChallengeType)) {
            return false;
        }
        ChallengeSelection expectedChallengeSelection = new ChallengeSelection(
                expectedChallengeType,
                challengePolicy.normalizeExpectedChallengeSubType(expectedChallengeType, expectedChallengeSubType));
        return challengeSessionService.refreshPendingChallengeSelection(
                email,
                deviceFingerprint,
                expectedChallengeSelection);
    }

    private RegisterPrecheckResult buildEmailCodeCooldownResultIfNecessary(RiskSnapshot riskSnapshot, long passedAt) {
        if (passedAt <= 0L) {
            return null;
        }
        long retryAfterMs = Math.max(0L, passedAt - System.currentTimeMillis());
        if (retryAfterMs <= 0L) {
            return null;
        }
        long retryAfterSeconds = Math.max(1L, (retryAfterMs + 999L) / 1000L);
        return RegisterPrecheckResult.builder()
                .success(false)
                .message("Please wait " + retryAfterSeconds + "s before resending the email code.")
                .totalScore(riskSnapshot.totalScore())
                .riskLevel(riskSnapshot.riskLevel())
                .emailCodeRetryAfterMs(retryAfterMs)
                .passedAt(passedAt)
                .emailCodeSent(false)
                .requirePhoneBinding(shouldRequirePhoneBinding(riskSnapshot.riskLevel()))
                .build();
    }

    private ChallengePassedState readChallengePassed(String flowId, String email, String deviceFingerprint) {
        if (StrUtil.hasBlank(flowId, email, deviceFingerprint)) {
            return ChallengePassedState.missing();
        }
        String value = stringRedisTemplate.opsForValue().get(challengePassedKey(flowId));
        if (StrUtil.isBlank(value)) {
            return ChallengePassedState.missing();
        }
        try {
            JSONObject payload = JSONUtil.parseObj(value);
            boolean matched = Boolean.TRUE.equals(payload.getBool("pass", false))
                    && Objects.equals(normalizeChallengeText(email), normalizeChallengeText(payload.getStr("email")))
                    && Objects.equals(
                    normalizeChallengeText(deviceFingerprint),
                    normalizeChallengeText(payload.getStr("deviceFingerprint")));
            if (!matched) {
                return ChallengePassedState.missing();
            }
            Long passedAt = payload.getLong("passedAt");
            return new ChallengePassedState(true, passedAt == null ? 0L : passedAt);
        } catch (RuntimeException ignored) {
            return ChallengePassedState.missing();
        }
    }

    private void saveChallengePassed(String flowId, String email, String deviceFingerprint, long passedAt) {
        if (StrUtil.hasBlank(flowId, email, deviceFingerprint)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", normalizeChallengeText(email));
        payload.put("deviceFingerprint", normalizeChallengeText(deviceFingerprint));
        payload.put("pass", true);
        payload.put("passedAt", passedAt);
        stringRedisTemplate.opsForValue().set(
                challengePassedKey(flowId),
                JSONUtil.toJsonStr(payload),
                RegisterRedisKeys.EMAIL_CODE_TTL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    private String challengePassedKey(String flowId) {
        return RegisterRedisKeys.EMAIL_CODE_CHALLENGE_PASSED_PREFIX + flowId;
    }

    private String normalizeChallengeText(String value) {
        return StrUtil.blankToDefault(value, "").trim();
    }

    private record ChallengePassedState(boolean matched, long passedAt) {

        private static ChallengePassedState missing() {
            return new ChallengePassedState(false, 0L);
        }
    }

    // test compatibility wrapper
    boolean verifyRequiredCaptcha(String challengeType,
                                  String publicIp,
                                  String captchaUuid,
                                  String captchaCode) {
        return captchaVerificationService.verifyRequiredCaptcha(
                challengeType,
                publicIp,
                captchaUuid,
                captchaCode);
    }

    // test compatibility wrapper
    static ChallengeSelection resolveL3ChallengeSelection(int bucket) {
        return ChallengePolicy.resolveL3ChallengeSelection(bucket);
    }

    private RegisterPrecheckResult validateRegisterInput(String email,
                                                         String username,
                                                         String rawPassword,
                                                         String deviceFingerprint) {
        if (!Validator.isEmail(email)) {
            return fail("请输入有效的电子邮箱地址");
        }
        if (StrUtil.isBlank(username)) {
            return fail("用户名不能为空");
        }
        if (StrUtil.isBlank(rawPassword) || rawPassword.length() < 6) {
            return fail("密码至少 6 位");
        }
        if (StrUtil.isBlank(deviceFingerprint)) {
            return fail("设备指纹不能为空");
        }
        return null;
    }

    private RegisterPrecheckResult buildCaptchaRequiredResult(Integer totalScore,
                                                              String riskLevel,
                                                              String challengeType,
                                                              String challengeSubType,
                                                              String email,
                                                              String deviceFingerprint) {
        Long retryAfterMs = null;
        Long waitUntilEpochMs = null;
        String message = "当前风险等级需要先通过验证码验证";
        if (isOperationTimeoutChallenge(challengeType)) {
            waitUntilEpochMs = challengeSessionService.ensureOperationTimeoutWaitUntil(email, deviceFingerprint);
            retryAfterMs = Math.max(0L, waitUntilEpochMs - System.currentTimeMillis());
            long retryAfterSeconds = Math.max(1L, (retryAfterMs + 999L) / 1000L);
            message = "当前操作过于频繁，请在 " + retryAfterSeconds + " 秒后重试";
        }
        return RegisterPrecheckResult.builder()
                .success(false)
                .message(message)
                .totalScore(totalScore)
                .riskLevel(riskLevel)
                .challengeType(challengeType)
                .challengeSubType(challengeSubType)
                .challengeSiteKey(captchaVerificationService.resolveChallengeSiteKey(challengeType))
                .retryAfterMs(retryAfterMs)
                .waitUntilEpochMs(waitUntilEpochMs)
                .emailCodeSent(false)
                .requirePhoneBinding(shouldRequirePhoneBinding(riskLevel))
                .build();
    }

    private RegisterPrecheckResult fail(String message) {
        return RegisterPrecheckResult.builder()
                .success(false)
                .message(message)
                .emailCodeSent(false)
                .requirePhoneBinding(false)
                .build();
    }

    /**
     * For OPERATION_TIMEOUT pending challenge:
     * - keep pending if wait key missing (legacy case, wait key will be created on response build)
     * - clear pending if wait window already elapsed
     */
    private ChallengeSelection normalizePendingChallengeSelection(String email,
                                                                  String deviceFingerprint,
                                                                  ChallengeSelection pendingChallengeSelection) {
        if (pendingChallengeSelection == null || !isOperationTimeoutChallenge(pendingChallengeSelection.type())) {
            return pendingChallengeSelection;
        }

        Long waitUntilEpochMs = challengeSessionService.readOperationTimeoutWaitUntil(email, deviceFingerprint);
        if (waitUntilEpochMs == null) {
            return pendingChallengeSelection;
        }
        if (waitUntilEpochMs > System.currentTimeMillis()) {
            return pendingChallengeSelection;
        }

        challengeSessionService.clearPendingChallengeSelection(email, deviceFingerprint);
        return null;
    }

    private boolean isOperationTimeoutChallenge(String challengeType) {
        return CHALLENGE_OPERATION_TIMEOUT.equalsIgnoreCase(StrUtil.blankToDefault(challengeType, ""));
    }

    /**
     * Registration risk policy:
     * L3/L4/L5 require phone binding after email code passes.
     * L1/L2 do not require it.
     * L6 is blocked in pre-auth chain.
     */
    private boolean shouldRequirePhoneBinding(String riskLevel) {
        if (riskLevel == null) {
            return false;
        }
        return "L3".equalsIgnoreCase(riskLevel)
                || "L4".equalsIgnoreCase(riskLevel)
                || "L5".equalsIgnoreCase(riskLevel);
    }
}
