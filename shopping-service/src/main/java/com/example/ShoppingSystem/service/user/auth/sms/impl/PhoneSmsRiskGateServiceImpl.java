package com.example.ShoppingSystem.service.user.auth.sms.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.service.captcha.hutool.HutoolCaptchaService;
import com.example.ShoppingSystem.service.captcha.thirdparty.ThirdPartyCaptchaService;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaService;
import com.example.ShoppingSystem.service.user.auth.login.impl.LoginChallengePolicy;
import com.example.ShoppingSystem.service.user.auth.login.impl.LoginChallengeSessionService;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import com.example.ShoppingSystem.service.user.auth.sms.PhoneSmsRiskGateService;
import com.example.ShoppingSystem.service.user.auth.sms.model.PhoneSmsRiskGateResult;
import org.springframework.stereotype.Service;

import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_CLOUDFLARE_TURNSTILE;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_GOOGLE_RECAPTCHA_V2;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_GOOGLE_RECAPTCHA_V3_LEGACY;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HCAPTCHA;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HUTOOL_SHEAR;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_TIANAI;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_CONCAT;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_ROTATE;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_SLIDER;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.SUBTYPE_TIANAI_WORD_IMAGE_CLICK;

/**
 * 手机短信发送前置风控网关。
 * <p>
 * 这里是短信发送前最后一道“安全闸门”：
 * 1. L6 直接阻断，不允许通过验证码继续消耗短信资源；
 * 2. L1/L2/L3/L4/L5 按短信场景专属策略选择验证码；
 * 3. 验证码通过后才允许进入手机号格式校验之后的短信发送链路；
 * 4. pending challenge 复用登录 challenge 的 Redis 会话服务，TTL 与登录验证码保持 5 分钟。
 */
@Service
public class PhoneSmsRiskGateServiceImpl implements PhoneSmsRiskGateService {

    private static final String SMS_CAPTCHA_TYPE = "login";
    private static final String CHALLENGE_IDENTITY_SEPARATOR = ":";

    private final LoginChallengeSessionService loginChallengeSessionService;
    private final HutoolCaptchaService hutoolCaptchaService;
    private final TianaiCaptchaService tianaiCaptchaService;
    private final ThirdPartyCaptchaService thirdPartyCaptchaService;
    private final LoginChallengePolicy loginChallengePolicy;

    public PhoneSmsRiskGateServiceImpl(LoginChallengeSessionService loginChallengeSessionService,
                                       HutoolCaptchaService hutoolCaptchaService,
                                       TianaiCaptchaService tianaiCaptchaService,
                                       ThirdPartyCaptchaService thirdPartyCaptchaService,
                                       LoginChallengePolicy loginChallengePolicy) {
        this.loginChallengeSessionService = loginChallengeSessionService;
        this.hutoolCaptchaService = hutoolCaptchaService;
        this.tianaiCaptchaService = tianaiCaptchaService;
        this.thirdPartyCaptchaService = thirdPartyCaptchaService;
        this.loginChallengePolicy = loginChallengePolicy;
    }

    @Override
    public PhoneSmsRiskGateResult checkOrVerify(String scene,
                                                String normalizedPhone,
                                                String preAuthToken,
                                                String deviceFingerprint,
                                                String riskLevel,
                                                String remoteIp,
                                                String captchaUuid,
                                                String captchaCode) {
        String normalizedRiskLevel = loginChallengePolicy.normalizeRiskLevel(riskLevel);
        if ("L6".equals(normalizedRiskLevel)) {
            // L6 是阻断级别，短信接口必须直接拒绝，不能继续弹验证码或发送短信。
            return PhoneSmsRiskGateResult.blocked(normalizedRiskLevel, "Current network risk is too high. SMS sending is blocked.");
        }

        String challengeIdentity = buildChallengeIdentity(scene, normalizedPhone);
        if (StrUtil.hasBlank(challengeIdentity, deviceFingerprint)) {
            return PhoneSmsRiskGateResult.rejected(normalizedRiskLevel, "SMS_RISK_CONTEXT_MISSING", "SMS security context is missing.");
        }

        ChallengeSelection pendingSelection =
                loginChallengeSessionService.readPendingChallengeSelection(challengeIdentity, deviceFingerprint);
        ChallengeSelection requiredSelection = pendingSelection == null
                ? resolveSmsChallengeSelection(normalizedRiskLevel)
                : pendingSelection;

        if (requiredSelection == null || StrUtil.isBlank(requiredSelection.type())) {
            return PhoneSmsRiskGateResult.allowed(normalizedRiskLevel);
        }

        if (StrUtil.isBlank(captchaCode)) {
            // 首次命中风控时，只保存 pending challenge 并把类型返回给前端，让前端拉起对应验证码组件。
            loginChallengeSessionService.savePendingChallengeSelection(challengeIdentity, deviceFingerprint, requiredSelection);
            return PhoneSmsRiskGateResult.challengeRequired(
                    normalizedRiskLevel,
                    requiredSelection.type(),
                    requiredSelection.subType(),
                    resolveChallengeSiteKey(requiredSelection.type()),
                    challengeIdentity
            );
        }

        if (!verifyCaptcha(requiredSelection, remoteIp, captchaUuid, captchaCode)) {
            // 验证失败时保留 pending challenge，让前端可以继续展示同一种验证码并重试。
            loginChallengeSessionService.refreshPendingChallengeSelection(challengeIdentity, deviceFingerprint, requiredSelection);
            return PhoneSmsRiskGateResult.challengeRequired(
                    normalizedRiskLevel,
                    requiredSelection.type(),
                    requiredSelection.subType(),
                    resolveChallengeSiteKey(requiredSelection.type()),
                    challengeIdentity
            );
        }

        // 验证通过后立即清理 pending challenge，防止一次验证码被重复用于多次短信发送。
        loginChallengeSessionService.clearPendingChallengeSelection(challengeIdentity, deviceFingerprint);
        return PhoneSmsRiskGateResult.allowed(normalizedRiskLevel);
    }

    /**
     * 短信专属 challenge 分流策略。
     * <p>
     * 与登录首屏风控分开定义，原因是“发送短信”会消耗外部资源，
     * 因此 L1/L2 也需要轻量验证码，而不是完全放行。
     */
    private ChallengeSelection resolveSmsChallengeSelection(String riskLevel) {
        return switch (riskLevel) {
            case "L1", "L2" -> resolveL1L2SmsChallenge();
            case "L3" -> randomHalf(CHALLENGE_CLOUDFLARE_TURNSTILE, CHALLENGE_HCAPTCHA);
            case "L4" -> randomHalf(CHALLENGE_GOOGLE_RECAPTCHA_V2, CHALLENGE_HCAPTCHA);
            case "L5" -> new ChallengeSelection(CHALLENGE_GOOGLE_RECAPTCHA_V2, null);
            default -> ChallengeSelection.none();
        };
    }

    /**
     * L1/L2：天爱四种玩法 + Hutool，各占 1/5。
     */
    private ChallengeSelection resolveL1L2SmsChallenge() {
        int bucket = RandomUtil.randomInt(5);
        return switch (bucket) {
            case 0 -> new ChallengeSelection(CHALLENGE_HUTOOL_SHEAR, null);
            case 1 -> new ChallengeSelection(CHALLENGE_TIANAI, SUBTYPE_TIANAI_SLIDER);
            case 2 -> new ChallengeSelection(CHALLENGE_TIANAI, SUBTYPE_TIANAI_ROTATE);
            case 3 -> new ChallengeSelection(CHALLENGE_TIANAI, SUBTYPE_TIANAI_CONCAT);
            default -> new ChallengeSelection(CHALLENGE_TIANAI, SUBTYPE_TIANAI_WORD_IMAGE_CLICK);
        };
    }

    private ChallengeSelection randomHalf(String left, String right) {
        return RandomUtil.randomBoolean()
                ? new ChallengeSelection(left, null)
                : new ChallengeSelection(right, null);
    }

    private boolean verifyCaptcha(ChallengeSelection selection,
                                  String remoteIp,
                                  String captchaUuid,
                                  String captchaCode) {
        return switch (selection.type()) {
            case CHALLENGE_HUTOOL_SHEAR ->
                    hutoolCaptchaService.validateCaptcha(SMS_CAPTCHA_TYPE, captchaUuid, captchaCode);
            case CHALLENGE_TIANAI ->
                    tianaiCaptchaService.validateCaptcha(captchaUuid, captchaCode);
            case CHALLENGE_CLOUDFLARE_TURNSTILE ->
                    thirdPartyCaptchaService.validateTurnstile(captchaCode, remoteIp);
            case CHALLENGE_HCAPTCHA ->
                    thirdPartyCaptchaService.validateHCaptcha(captchaCode, remoteIp);
            case CHALLENGE_GOOGLE_RECAPTCHA_V2, CHALLENGE_GOOGLE_RECAPTCHA_V3_LEGACY ->
                    thirdPartyCaptchaService.validateRecaptcha(captchaCode, remoteIp);
            default -> false;
        };
    }

    private String resolveChallengeSiteKey(String challengeType) {
        if (CHALLENGE_CLOUDFLARE_TURNSTILE.equals(challengeType)) {
            return thirdPartyCaptchaService.getTurnstileSiteKey();
        }
        if (CHALLENGE_HCAPTCHA.equals(challengeType)) {
            return thirdPartyCaptchaService.getHCaptchaSiteKey();
        }
        if (CHALLENGE_GOOGLE_RECAPTCHA_V2.equals(challengeType)
                || CHALLENGE_GOOGLE_RECAPTCHA_V3_LEGACY.equals(challengeType)) {
            return thirdPartyCaptchaService.getRecaptchaSiteKey();
        }
        return null;
    }

    private String buildChallengeIdentity(String scene, String normalizedPhone) {
        if (StrUtil.hasBlank(scene, normalizedPhone)) {
            return null;
        }
        return "sms"
                + CHALLENGE_IDENTITY_SEPARATOR
                + scene.trim().toUpperCase()
                + CHALLENGE_IDENTITY_SEPARATOR
                + normalizedPhone.trim();
    }
}
