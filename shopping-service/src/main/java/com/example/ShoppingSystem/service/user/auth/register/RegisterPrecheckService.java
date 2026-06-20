package com.example.ShoppingSystem.service.user.auth.register;

import com.example.ShoppingSystem.service.user.auth.register.model.RegisterPrecheckResult;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants;
import com.example.ShoppingSystem.service.user.auth.risk.AuthRiskSnapshot;

/**
 * 注册前置校验服务接口。
 */
public interface RegisterPrecheckService {

    public static final String CHALLENGE_HUTOOL_SHEAR = RegisterChallengeConstants.CHALLENGE_HUTOOL_SHEAR;
    public static final String CHALLENGE_TIANAI = RegisterChallengeConstants.CHALLENGE_TIANAI;
    public static final String CHALLENGE_CLOUDFLARE_TURNSTILE = RegisterChallengeConstants.CHALLENGE_CLOUDFLARE_TURNSTILE;
    public static final String CHALLENGE_HCAPTCHA = RegisterChallengeConstants.CHALLENGE_HCAPTCHA;
    public static final String CHALLENGE_OPERATION_TIMEOUT = RegisterChallengeConstants.CHALLENGE_OPERATION_TIMEOUT;
    public static final String SUBTYPE_TIANAI_SLIDER = RegisterChallengeConstants.SUBTYPE_TIANAI_SLIDER;
    public static final String SUBTYPE_TIANAI_ROTATE = RegisterChallengeConstants.SUBTYPE_TIANAI_ROTATE;
    public static final String SUBTYPE_TIANAI_CONCAT = RegisterChallengeConstants.SUBTYPE_TIANAI_CONCAT;
    public static final String SUBTYPE_TIANAI_WORD_IMAGE_CLICK = RegisterChallengeConstants.SUBTYPE_TIANAI_WORD_IMAGE_CLICK;

    RegisterPrecheckResult resolveRegisterEmailCodeChallenge(String email,
                                                             String username,
                                                             String rawPassword,
                                                             String deviceFingerprint,
                                                             String publicIp);

    RegisterPrecheckResult resolveRegisterEmailCodeChallenge(String email,
                                                             String username,
                                                             String rawPassword,
                                                             String deviceFingerprint,
                                                             String publicIp,
                                                             AuthRiskSnapshot riskSnapshotOverride);

    RegisterPrecheckResult resolveRegisterEmailCodeChallenge(String flowId,
                                                             String email,
                                                             String username,
                                                             String rawPassword,
                                                             String deviceFingerprint,
                                                             String publicIp,
                                                             AuthRiskSnapshot riskSnapshotOverride);

    RegisterPrecheckResult sendRegisterEmailCodeAfterCaptcha(String flowId,
                                                             boolean allowPassedChallengeReuse,
                                                             String email,
                                                             String username,
                                                             String rawPassword,
                                                             String deviceFingerprint,
                                                             String publicIp,
                                                             String captchaUuid,
                                                             String captchaCode);

    RegisterPrecheckResult sendRegisterEmailCodeAfterCaptcha(String flowId,
                                                             boolean allowPassedChallengeReuse,
                                                             String email,
                                                             String username,
                                                             String rawPassword,
                                                             String deviceFingerprint,
                                                             String publicIp,
                                                             String captchaUuid,
                                                             String captchaCode,
                                                             AuthRiskSnapshot riskSnapshotOverride);

    boolean refreshPendingChallengeSelection(String email,
                                            String deviceFingerprint,
                                            String expectedChallengeType);

    default boolean refreshPendingChallengeSelection(String email,
                                                     String deviceFingerprint,
                                                     String expectedChallengeType,
                                                     String expectedChallengeSubType) {
        return refreshPendingChallengeSelection(email, deviceFingerprint, expectedChallengeType);
    }
}
