package com.example.ShoppingSystem.service.user.auth.register;

import com.example.ShoppingSystem.service.user.auth.register.model.RegisterPrecheckResult;

/**
 * 注册前置校验服务接口。
 */
public interface RegisterPrecheckService {

    RegisterPrecheckResult resolveRegisterEmailCodeChallenge(String email,
                                                             String username,
                                                             String rawPassword,
                                                             String deviceFingerprint,
                                                             String publicIp);

    RegisterPrecheckResult sendRegisterEmailCodeAfterCaptcha(String flowId,
                                                             boolean allowPassedChallengeReuse,
                                                             String email,
                                                             String username,
                                                             String rawPassword,
                                                             String deviceFingerprint,
                                                             String publicIp,
                                                             String captchaUuid,
                                                             String captchaCode);

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
