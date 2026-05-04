package com.example.ShoppingSystem.service.user.auth.login.impl;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.service.user.auth.register.impl.ChallengePolicy;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import org.springframework.stereotype.Service;

import java.util.Locale;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_CLOUDFLARE_TURNSTILE;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_GOOGLE_RECAPTCHA_V2;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_GOOGLE_RECAPTCHA_V3_LEGACY;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HCAPTCHA;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HUTOOL_SHEAR;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_OPERATION_TIMEOUT;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_TIANAI;

@Service
public class LoginChallengePolicy {

    public static final String CHALLENGE_WAF_REQUIRED = "WAF_REQUIRED";

    private final ChallengePolicy challengePolicy;

    public LoginChallengePolicy(ChallengePolicy challengePolicy) {
        this.challengePolicy = challengePolicy;
    }

    public ChallengeSelection resolveChallengeSelection(String riskLevel,
                                                        String email,
                                                        String deviceFingerprint) {
        return switch (normalizeRiskLevel(riskLevel)) {
            case "L2" -> new ChallengeSelection(CHALLENGE_HUTOOL_SHEAR, null);
            case "L3" -> new ChallengeSelection(CHALLENGE_TIANAI, challengePolicy.randomTianaiSubType());
            case "L4" -> resolveL4ChallengeSelection(email, deviceFingerprint);
            case "L5" -> new ChallengeSelection(CHALLENGE_WAF_REQUIRED, null);
            case "L6" -> new ChallengeSelection(CHALLENGE_OPERATION_TIMEOUT, null);
            default -> ChallengeSelection.none();
        };
    }

    public boolean shouldRequireTwoFactors(String riskLevel) {
        String normalized = normalizeRiskLevel(riskLevel);
        return "L3".equals(normalized) || "L4".equals(normalized) || "L5".equals(normalized);
    }

    public boolean shouldRequirePhoneBinding(String riskLevel) {
        return shouldRequireTwoFactors(riskLevel);
    }

    public boolean isCaptchaChallenge(String challengeType) {
        return CHALLENGE_HUTOOL_SHEAR.equals(challengeType)
                || CHALLENGE_TIANAI.equals(challengeType)
                || CHALLENGE_CLOUDFLARE_TURNSTILE.equals(challengeType)
                || CHALLENGE_HCAPTCHA.equals(challengeType)
                || CHALLENGE_GOOGLE_RECAPTCHA_V2.equals(challengeType)
                || CHALLENGE_GOOGLE_RECAPTCHA_V3_LEGACY.equals(challengeType);
    }

    public boolean isWafChallenge(String challengeType) {
        return CHALLENGE_WAF_REQUIRED.equals(challengeType);
    }

    public boolean isOperationTimeoutChallenge(String challengeType) {
        return CHALLENGE_OPERATION_TIMEOUT.equals(challengeType);
    }

    public String normalizeExpectedChallengeSubType(String expectedChallengeType, String expectedChallengeSubType) {
        return challengePolicy.normalizeExpectedChallengeSubType(expectedChallengeType, expectedChallengeSubType);
    }

    public String normalizeRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return "L1";
        }
        String normalized = riskLevel.trim().toUpperCase(Locale.ROOT);
        if (StrUtil.isBlank(normalized)) {
            return "L1";
        }
        return switch (normalized) {
            case "L1", "L2", "L3", "L4", "L5", "L6" -> normalized;
            default -> "L1";
        };
    }

    private ChallengeSelection resolveL4ChallengeSelection(String email, String deviceFingerprint) {
        int bucket = Math.floorMod((StrUtil.nullToEmpty(email) + "|" + StrUtil.nullToEmpty(deviceFingerprint)).hashCode(), 3);
        if (bucket == 0) {
            return new ChallengeSelection(CHALLENGE_CLOUDFLARE_TURNSTILE, null);
        }
        if (bucket == 1) {
            return new ChallengeSelection(CHALLENGE_HCAPTCHA, null);
        }
        return new ChallengeSelection(CHALLENGE_GOOGLE_RECAPTCHA_V2, null);
    }
}
