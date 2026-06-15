package com.example.ShoppingSystem.service.user.auth.passwordreset;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaStrategyRegistry;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaVerifyRequest;
import com.example.ShoppingSystem.service.user.auth.login.impl.LoginChallengeSessionService;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetResult;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import org.springframework.stereotype.Service;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_CLOUDFLARE_TURNSTILE;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_GOOGLE_RECAPTCHA_V2;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HCAPTCHA;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HUTOOL_SHEAR;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_TIANAI;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_CONCAT;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_ROTATE;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_SLIDER;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_WORD_IMAGE_CLICK;

@Service
public class PasswordResetEmailCaptchaRiskGateService {

    public static final String PASSWORD_RESET_CAPTCHA_TYPE = "password-reset";

    private static final String CHALLENGE_IDENTITY_PREFIX = "password-reset-email:";

    private final LoginChallengeSessionService loginChallengeSessionService;
    private final CaptchaStrategyRegistry captchaStrategyRegistry;

    public PasswordResetEmailCaptchaRiskGateService(LoginChallengeSessionService loginChallengeSessionService,
                                                    CaptchaStrategyRegistry captchaStrategyRegistry) {
        this.loginChallengeSessionService = loginChallengeSessionService;
        this.captchaStrategyRegistry = captchaStrategyRegistry;
    }

    public PasswordResetResult checkOrVerify(String email,
                                             String deviceFingerprint,
                                             String riskLevel,
                                             String remoteIp,
                                             String captchaUuid,
                                             String captchaCode) {
        String normalizedRiskLevel = normalizeRiskLevel(riskLevel);
        ChallengeSelection requiredSelection = resolveChallengeSelection(normalizedRiskLevel);
        if (requiredSelection == null || StrUtil.isBlank(requiredSelection.type())) {
            return null;
        }

        String challengeIdentity = buildChallengeIdentity(email);
        if (StrUtil.hasBlank(challengeIdentity, deviceFingerprint)) {
            return PasswordResetResult.fail(
                    "PASSWORD_RESET_RISK_CONTEXT_MISSING",
                    "Password reset security context is missing."
            );
        }

        ChallengeSelection pendingSelection =
                loginChallengeSessionService.readPendingChallengeSelection(challengeIdentity, deviceFingerprint);
        if (pendingSelection != null && StrUtil.isNotBlank(pendingSelection.type())) {
            requiredSelection = pendingSelection;
        }

        if (StrUtil.isBlank(captchaCode)) {
            loginChallengeSessionService.savePendingChallengeSelection(
                    challengeIdentity,
                    deviceFingerprint,
                    requiredSelection);
            return captchaRequiredResult(normalizedRiskLevel, requiredSelection);
        }

        if (!verifyCaptcha(requiredSelection, remoteIp, captchaUuid, captchaCode)) {
            loginChallengeSessionService.refreshPendingChallengeSelection(
                    challengeIdentity,
                    deviceFingerprint,
                    requiredSelection);
            return captchaRequiredResult(normalizedRiskLevel, requiredSelection);
        }

        loginChallengeSessionService.clearPendingChallengeSelection(challengeIdentity, deviceFingerprint);
        return null;
    }

    public boolean refreshPendingChallengeSelection(String email,
                                                    String deviceFingerprint,
                                                    String expectedChallengeType,
                                                    String expectedChallengeSubType) {
        String challengeIdentity = buildChallengeIdentity(email);
        if (StrUtil.hasBlank(challengeIdentity, deviceFingerprint, expectedChallengeType)) {
            return false;
        }
        ChallengeSelection currentSelection =
                loginChallengeSessionService.readPendingChallengeSelection(challengeIdentity, deviceFingerprint);
        ChallengeSelection expectedSelection = new ChallengeSelection(expectedChallengeType, expectedChallengeSubType);
        if (!isSameChallengeSelection(currentSelection, expectedSelection)) {
            return false;
        }
        return loginChallengeSessionService.refreshPendingChallengeSelection(
                challengeIdentity,
                deviceFingerprint,
                expectedSelection
        );
    }

    private ChallengeSelection resolveChallengeSelection(String riskLevel) {
        return switch (riskLevel) {
            case "L2" -> new ChallengeSelection(CHALLENGE_HUTOOL_SHEAR, null);
            case "L3" -> randomTianaiChallenge();
            case "L4" -> randomThirdPartyChallenge();
            default -> ChallengeSelection.none();
        };
    }

    private ChallengeSelection randomTianaiChallenge() {
        return switch (RandomUtil.randomInt(4)) {
            case 0 -> new ChallengeSelection(CHALLENGE_TIANAI, SUBTYPE_TIANAI_SLIDER);
            case 1 -> new ChallengeSelection(CHALLENGE_TIANAI, SUBTYPE_TIANAI_ROTATE);
            case 2 -> new ChallengeSelection(CHALLENGE_TIANAI, SUBTYPE_TIANAI_CONCAT);
            default -> new ChallengeSelection(CHALLENGE_TIANAI, SUBTYPE_TIANAI_WORD_IMAGE_CLICK);
        };
    }

    private ChallengeSelection randomThirdPartyChallenge() {
        return switch (RandomUtil.randomInt(3)) {
            case 0 -> new ChallengeSelection(CHALLENGE_CLOUDFLARE_TURNSTILE, null);
            case 1 -> new ChallengeSelection(CHALLENGE_HCAPTCHA, null);
            default -> new ChallengeSelection(CHALLENGE_GOOGLE_RECAPTCHA_V2, null);
        };
    }

    private boolean verifyCaptcha(ChallengeSelection selection,
                                  String remoteIp,
                                  String captchaUuid,
                                  String captchaCode) {
        return captchaStrategyRegistry.verify(new CaptchaVerifyRequest(
                selection.type(),
                selection.subType(),
                PASSWORD_RESET_CAPTCHA_TYPE,
                captchaUuid,
                captchaCode,
                remoteIp
        ));
    }

    private PasswordResetResult captchaRequiredResult(String riskLevel, ChallengeSelection selection) {
        return PasswordResetResult.captchaRequired(
                riskLevel,
                selection.type(),
                selection.subType(),
                resolveChallengeSiteKey(selection.type(), selection.subType())
        );
    }

    private String resolveChallengeSiteKey(String challengeType, String challengeSubType) {
        return StrUtil.blankToDefault(captchaStrategyRegistry.siteKey(challengeType, challengeSubType), "");
    }

    private String buildChallengeIdentity(String email) {
        String normalizedEmail = StrUtil.blankToDefault(email, "").trim().toLowerCase();
        return StrUtil.isBlank(normalizedEmail) ? "" : CHALLENGE_IDENTITY_PREFIX + normalizedEmail;
    }

    private boolean isSameChallengeSelection(ChallengeSelection currentSelection, ChallengeSelection expectedSelection) {
        return currentSelection != null
                && expectedSelection != null
                && StrUtil.equals(currentSelection.type(), expectedSelection.type())
                && StrUtil.equals(
                        StrUtil.nullToEmpty(currentSelection.subType()),
                        StrUtil.nullToEmpty(expectedSelection.subType()));
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = StrUtil.blankToDefault(riskLevel, "L1").trim().toUpperCase();
        return switch (normalized) {
            case "L1", "L2", "L3", "L4", "L5", "L6" -> normalized;
            default -> "L1";
        };
    }
}
