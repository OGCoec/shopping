package com.example.ShoppingSystem.service.user.auth.passwordreset;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaStrategyRegistry;
import com.example.ShoppingSystem.service.captcha.strategy.CaptchaVerifyRequest;
import com.example.ShoppingSystem.service.user.auth.login.LoginChallengeSessionService;
import com.example.ShoppingSystem.service.user.auth.passwordreset.model.PasswordResetResult;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_CLOUDFLARE_TURNSTILE;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_GOOGLE_RECAPTCHA_V2;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HCAPTCHA;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HUTOOL_SHEAR;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_TIANAI;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_CONCAT;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_ROTATE;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_SLIDER;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_WORD_IMAGE_CLICK;

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
