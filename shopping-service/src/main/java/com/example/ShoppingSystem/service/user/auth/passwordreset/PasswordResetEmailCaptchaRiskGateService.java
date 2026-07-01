package com.example.ShoppingSystem.service.user.auth.passwordreset;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetResult;
public interface PasswordResetEmailCaptchaRiskGateService {
    public static final String PASSWORD_RESET_CAPTCHA_TYPE = "password-reset";

    public PasswordResetResult checkOrVerify(String email,
                                             String deviceFingerprint,
                                             String riskLevel,
                                             String remoteIp,
                                             String captchaUuid,
                                             String captchaCode);

    public boolean refreshPendingChallengeSelection(String email,
                                                    String deviceFingerprint,
                                                    String expectedChallengeType,
                                                    String expectedChallengeSubType);
}
