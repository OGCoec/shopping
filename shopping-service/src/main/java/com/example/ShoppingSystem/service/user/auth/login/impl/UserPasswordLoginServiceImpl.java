package com.example.ShoppingSystem.service.user.auth.login.impl;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.phone.PhoneNumberValidationService;
import com.example.ShoppingSystem.redisdata.LoginRedisKeys;
import com.example.ShoppingSystem.service.captcha.hutool.HutoolCaptchaService;
import com.example.ShoppingSystem.service.captcha.thirdparty.ThirdPartyCaptchaService;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaService;
import com.example.ShoppingSystem.service.user.auth.login.LoginFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.login.UserPasswordLoginService;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFactor;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowSession;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowStartResult;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowStep;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowValidationResult;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginVerificationResult;
import com.example.ShoppingSystem.service.user.auth.register.RegisterEmailCodeMessagePublisher;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBoundCountingBloomService;
import com.example.ShoppingSystem.service.user.auth.sms.SmsCodeService;
import com.example.ShoppingSystem.service.user.auth.sms.PhoneSmsRiskGateService;
import com.example.ShoppingSystem.service.user.auth.sms.model.PhoneSmsRiskGateResult;
import com.example.ShoppingSystem.service.user.auth.sms.model.SmsCodeSendResult;
import com.example.ShoppingSystem.service.user.auth.sms.model.SmsCodeVerifyResult;
import com.example.ShoppingSystem.service.user.auth.totp.UserTotpService;
import com.example.ShoppingSystem.service.user.auth.totp.model.TotpVerificationResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.example.ShoppingSystem.service.user.auth.login.impl.LoginChallengePolicy.CHALLENGE_WAF_REQUIRED;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_CLOUDFLARE_TURNSTILE;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_GOOGLE_RECAPTCHA_V2;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_GOOGLE_RECAPTCHA_V3_LEGACY;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HCAPTCHA;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_HUTOOL_SHEAR;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_OPERATION_TIMEOUT;
import static com.example.ShoppingSystem.service.user.auth.register.model.RegisterChallengeConstants.CHALLENGE_TIANAI;

@Service
public class UserPasswordLoginServiceImpl implements UserPasswordLoginService {

    private static final String LOGIN_CAPTCHA_TYPE = "login";
    private static final String LOGIN_PATH = "/shopping/user/log-in";
    private static final String PASSWORD_PATH = "/shopping/user/log-in/password";
    private static final String EMAIL_VERIFICATION_PATH = "/shopping/user/email-verification";
    private static final String TOTP_VERIFICATION_PATH = "/shopping/user/totp-verification";
    private static final String ADD_PHONE_PATH = "/shopping/user/add-phone";
    private static final String LOGIN_EMAIL_VERIFICATION_PATH = "/shopping/user/email-verification?mode=login";
    private static final String LOGIN_TOTP_VERIFICATION_PATH = "/shopping/user/totp-verification?mode=login";
    private static final String LOGIN_ADD_PHONE_PATH = "/shopping/user/add-phone?mode=login";
    private static final String SESSION_ENDED_PATH = "/shopping/user/session-ended";
    private static final String AUTHENTICATED_PATH = "/";
    private static final String ERROR_INVALID_STATE = "INVALID_STATE";
    private static final String ERROR_PHONE_LOGIN_INVALID_PHONE = "PHONE_LOGIN_INVALID_PHONE";
    private static final String ERROR_PHONE_LOGIN_BLOOM_MISS = "PHONE_LOGIN_BLOOM_MISS";
    private static final String ERROR_PHONE_LOGIN_DB_MISS = "PHONE_LOGIN_DB_MISS";
    private static final String ERROR_PHONE_LOGIN_NOT_IMPLEMENTED = "PHONE_LOGIN_NOT_IMPLEMENTED";
    private static final String ERROR_PHONE_ALREADY_BOUND = "PHONE_ALREADY_BOUND";
    private static final String ERROR_PHONE_BOUND_BLOOM_UNAVAILABLE = "PHONE_BOUND_BLOOM_UNAVAILABLE";
    private static final String INVALID_STATE_MESSAGE = "验证过程中出错 (invalid_state)。请重试。";

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final LoginFlowSessionService loginFlowSessionService;
    private final LoginChallengePolicy loginChallengePolicy;
    private final LoginChallengeSessionService loginChallengeSessionService;
    private final HutoolCaptchaService hutoolCaptchaService;
    private final TianaiCaptchaService tianaiCaptchaService;
    private final ThirdPartyCaptchaService thirdPartyCaptchaService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RegisterEmailCodeMessagePublisher registerEmailCodeMessagePublisher;
    private final PasswordEncoder passwordEncoder;
    private final UserTotpService userTotpService;
    private final SmsCodeService smsCodeService;
    private final PhoneSmsRiskGateService phoneSmsRiskGateService;
    private final PhoneNumberValidationService phoneNumberValidationService;
    private final PhoneBoundCountingBloomService phoneBoundCountingBloomService;

    public UserPasswordLoginServiceImpl(UserLoginIdentityMapper userLoginIdentityMapper,
                                        LoginFlowSessionService loginFlowSessionService,
                                        LoginChallengePolicy loginChallengePolicy,
                                        LoginChallengeSessionService loginChallengeSessionService,
                                        HutoolCaptchaService hutoolCaptchaService,
                                        TianaiCaptchaService tianaiCaptchaService,
                                        ThirdPartyCaptchaService thirdPartyCaptchaService,
                                        StringRedisTemplate stringRedisTemplate,
                                        RegisterEmailCodeMessagePublisher registerEmailCodeMessagePublisher,
                                        PasswordEncoder passwordEncoder,
                                        UserTotpService userTotpService,
                                        SmsCodeService smsCodeService,
                                        PhoneSmsRiskGateService phoneSmsRiskGateService,
                                        PhoneNumberValidationService phoneNumberValidationService,
                                        PhoneBoundCountingBloomService phoneBoundCountingBloomService) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.loginFlowSessionService = loginFlowSessionService;
        this.loginChallengePolicy = loginChallengePolicy;
        this.loginChallengeSessionService = loginChallengeSessionService;
        this.hutoolCaptchaService = hutoolCaptchaService;
        this.tianaiCaptchaService = tianaiCaptchaService;
        this.thirdPartyCaptchaService = thirdPartyCaptchaService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.registerEmailCodeMessagePublisher = registerEmailCodeMessagePublisher;
        this.passwordEncoder = passwordEncoder;
        this.userTotpService = userTotpService;
        this.smsCodeService = smsCodeService;
        this.phoneSmsRiskGateService = phoneSmsRiskGateService;
        this.phoneNumberValidationService = phoneNumberValidationService;
        this.phoneBoundCountingBloomService = phoneBoundCountingBloomService;
    }

    @Override
    @Transactional
    public LoginFlowStartResult startFlow(String email,
                                          String deviceFingerprint,
                                          String preAuthToken,
                                          String riskLevel,
                                          String publicIp,
                                          String captchaUuid,
                                          String captchaCode,
                                          boolean wafResumeRequest) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedDeviceFingerprint = normalizeText(deviceFingerprint);
        String normalizedPreAuthToken = normalizeText(preAuthToken);
        if (!Validator.isEmail(normalizedEmail)) {
            return startFail("Please enter a valid email address.");
        }
        if (StrUtil.hasBlank(normalizedDeviceFingerprint, normalizedPreAuthToken)) {
            return startFail("Login context is missing, please refresh and try again.");
        }

        String normalizedRiskLevel = loginChallengePolicy.normalizeRiskLevel(riskLevel);
        LoginFlowStartResult challengeResult = requireLoginChallengeIfNeeded(
                normalizedEmail,
                normalizedDeviceFingerprint,
                normalizedPreAuthToken,
                normalizedRiskLevel,
                publicIp,
                captchaUuid,
                captchaCode,
                wafResumeRequest
        );
        if (challengeResult != null) {
            return challengeResult;
        }

        LoginFlowSession session = loginFlowSessionService.startFlow(
                normalizedEmail,
                null,
                normalizedRiskLevel,
                candidateLoginFactors(),
                loginChallengePolicy.shouldRequireTwoFactors(normalizedRiskLevel) ? 2 : 1,
                false,
                normalizedDeviceFingerprint,
                normalizedPreAuthToken
        );

        return LoginFlowStartResult.builder()
                .success(true)
                .message("ok")
                .flowId(session.getFlowId())
                .email(session.getEmail())
                .riskLevel(session.getRiskLevel())
                .step(session.getStep())
                .redirectPath(pathForStep(session.getStep()))
                .availableFactors(session.getAvailableFactors())
                .completedFactors(session.getCompletedFactors())
                .requiredFactorCount(session.getRequiredFactorCount())
                .requirePhoneBinding(session.isRequirePhoneBinding())
                .build();
    }

    @Override
    public LoginVerificationResult currentFlow(String flowId, String preAuthToken) {
        LoginFlowValidationResult validationResult = loginFlowSessionService.validate(flowId, preAuthToken);
        if (!validationResult.valid()) {
            return verifyFail("Login session expired, please restart.");
        }
        return fromSession(validationResult.session(), false, "ok");
    }

    @Override
    @Transactional
    public LoginVerificationResult verifyPassword(String flowId, String preAuthToken, String password) {
        LoginFlowValidationResult validationResult = loginFlowSessionService.validate(flowId, preAuthToken);
        if (!validationResult.valid()) {
            return verifyFail("Login session expired, please restart.");
        }
        LoginFlowSession session = validationResult.session();
        ResolvedLoginSession resolved = resolveSessionForFactor(session, LoginFactor.PASSWORD);
        if (resolved == null) {
            return verifyFail("Invalid email or password.");
        }
        UserLoginIdentity identity = resolved.identity();
        if (StrUtil.isBlank(identity.getEmailPasswordHash())
                || StrUtil.isBlank(password)
                || !passwordEncoder.matches(password, identity.getEmailPasswordHash())) {
            return verifyFail("Invalid email or password.");
        }
        return completeFactor(resolved.session(), LoginFactor.PASSWORD, identity);
    }

    @Override
    @Transactional
    public LoginVerificationResult sendEmailCode(String flowId, String preAuthToken) {
        LoginFlowValidationResult validationResult = loginFlowSessionService.validate(flowId, preAuthToken);
        if (!validationResult.valid()) {
            return verifyFail("Login session expired, please restart.");
        }
        LoginFlowSession session = validationResult.session();
        ResolvedLoginSession resolved = resolveSessionForFactor(session, LoginFactor.EMAIL_OTP);
        if (resolved == null) {
            return invalidState();
        }

        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(
                emailCodeKey(session.getFlowId()),
                code,
                LoginRedisKeys.EMAIL_CODE_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        registerEmailCodeMessagePublisher.publishRegisterEmailCode(
                session.getEmail(),
                code,
                LoginRedisKeys.EMAIL_CODE_TTL_MINUTES
        );
        LoginFlowSession updated = loginFlowSessionService.updateStep(
                resolved.session().getFlowId(),
                LoginFlowStep.EMAIL_VERIFICATION,
                resolved.session().getCompletedFactors(),
                resolved.session().isRequirePhoneBinding(),
                false
        );
        return fromSession(updated, false, "Email code sent.");
    }

    @Override
    @Transactional
    public LoginVerificationResult verifyEmailCode(String flowId, String preAuthToken, String emailCode) {
        LoginFlowValidationResult validationResult = loginFlowSessionService.validate(flowId, preAuthToken);
        if (!validationResult.valid()) {
            return verifyFail("Login session expired, please restart.");
        }
        LoginFlowSession session = validationResult.session();
        ResolvedLoginSession resolved = resolveSessionForFactor(session, LoginFactor.EMAIL_OTP);
        if (resolved == null) {
            return invalidState();
        }
        String normalizedEmailCode = normalizeText(emailCode);
        String cachedCode = stringRedisTemplate.opsForValue().get(emailCodeKey(resolved.session().getFlowId()));
        if (StrUtil.isBlank(cachedCode) || !StrUtil.equals(cachedCode, normalizedEmailCode)) {
            return verifyFail("Email code is incorrect or expired.");
        }
        stringRedisTemplate.delete(emailCodeKey(resolved.session().getFlowId()));
        return completeFactor(resolved.session(), LoginFactor.EMAIL_OTP, resolved.identity());
    }

    @Override
    @Transactional
    public LoginVerificationResult verifyTotp(String flowId, String preAuthToken, String code) {
        LoginFlowValidationResult validationResult = loginFlowSessionService.validate(flowId, preAuthToken);
        if (!validationResult.valid()) {
            return verifyFail("Login session expired, please restart.");
        }
        LoginFlowSession session = validationResult.session();
        ResolvedLoginSession resolved = resolveSessionForFactor(session, LoginFactor.TOTP);
        if (resolved == null) {
            return verifyFail("Verification failed.");
        }
        TotpVerificationResult result = userTotpService.verify(resolved.identity().getUserId(), code);
        if (!result.isSuccess()) {
            return verifyFail("Verification failed.");
        }
        return completeFactor(resolved.session(), LoginFactor.TOTP, resolved.identity());
    }

    @Override
    @Transactional
    public LoginVerificationResult checkPhoneLoginCandidate(String dialCode, String phoneNumber) {
        PhoneNumberValidationService.ValidationResult validationResult =
                phoneNumberValidationService.validateMobileLikeNumber(dialCode, phoneNumber);
        if (!validationResult.allowed() || StrUtil.isBlank(validationResult.normalizedE164())) {
            return verifyFail(ERROR_PHONE_LOGIN_INVALID_PHONE, "Phone number validation failed.");
        }
        String normalizedPhone = validationResult.normalizedE164();
        if (!phoneBoundCountingBloomService.mightContainVerifiedPhone(normalizedPhone)) {
            return verifyFail(ERROR_PHONE_LOGIN_BLOOM_MISS, "Phone number was not found by counting bloom filter.");
        }
        UserLoginIdentity identity = userLoginIdentityMapper.findVerifiedByPhone(normalizedPhone);
        if (identity == null || !isActive(identity)) {
            return verifyFail(ERROR_PHONE_LOGIN_DB_MISS, "Phone number was not found in verified login identities.");
        }
        phoneBoundCountingBloomService.addVerifiedPhoneAsync(normalizedPhone);
        return verifyFail(ERROR_PHONE_LOGIN_NOT_IMPLEMENTED, "Phone-first login is not connected yet.");
    }

    @Override
    @Transactional
    public LoginVerificationResult sendPhoneBindCode(String flowId,
                                                     String preAuthToken,
                                                     String dialCode,
                                                     String phoneNumber,
                                                     String clientIp,
                                                     String riskLevel,
                                                     String deviceFingerprint,
                                                     String captchaUuid,
                                                     String captchaCode) {
        LoginFlowValidationResult validationResult = loginFlowSessionService.validate(flowId, preAuthToken);
        if (!validationResult.valid()) {
            return verifyFail("Login session expired, please restart.");
        }
        LoginFlowSession session = validationResult.session();
        if (session.getStep() != LoginFlowStep.ADD_PHONE) {
            return verifyFail("Phone binding is not required for this login.");
        }
        LoginVerificationResult phoneValidationResult = validatePhoneCanBeBound(dialCode, phoneNumber);
        if (phoneValidationResult != null) {
            return phoneValidationResult;
        }
        String normalizedPhone = normalizeVerifiedPhone(dialCode, phoneNumber);
        PhoneSmsRiskGateResult gateResult = phoneSmsRiskGateService.checkOrVerify(
                PhoneSmsRiskGateService.SCENE_BIND_PHONE_SMS,
                normalizedPhone,
                preAuthToken,
                deviceFingerprint,
                riskLevel,
                clientIp,
                captchaUuid,
                captchaCode
        );
        if (!gateResult.isAllowed()) {
            return fromSmsRiskGate(gateResult);
        }
        SmsCodeSendResult sendResult = smsCodeService.sendBindPhoneCode(dialCode, phoneNumber, clientIp);
        if (!sendResult.isSuccess()) {
            return verifyFail(sendResult.getMessage());
        }
        return fromSession(session, false, sendResult.getMessage());
    }

    @Override
    @Transactional
    public LoginVerificationResult sendPhoneLoginCode(String preAuthToken,
                                                      String dialCode,
                                                      String phoneNumber,
                                                      String clientIp,
                                                      String riskLevel,
                                                      String deviceFingerprint,
                                                      String captchaUuid,
                                                      String captchaCode) {
        PhoneNumberValidationService.ValidationResult validationResult =
                phoneNumberValidationService.validateMobileLikeNumber(dialCode, phoneNumber);
        if (!validationResult.allowed() || StrUtil.isBlank(validationResult.normalizedE164())) {
            return verifyFail(ERROR_PHONE_LOGIN_INVALID_PHONE, "Phone number validation failed.");
        }

        String normalizedPhone = validationResult.normalizedE164();
        PhoneSmsRiskGateResult gateResult = phoneSmsRiskGateService.checkOrVerify(
                PhoneSmsRiskGateService.SCENE_PHONE_LOGIN_SMS,
                normalizedPhone,
                preAuthToken,
                deviceFingerprint,
                riskLevel,
                clientIp,
                captchaUuid,
                captchaCode
        );
        if (!gateResult.isAllowed()) {
            return fromSmsRiskGate(gateResult);
        }

        if (!phoneBoundCountingBloomService.mightContainVerifiedPhone(normalizedPhone)) {
            return verifyFail(ERROR_PHONE_LOGIN_BLOOM_MISS, "Phone number was not found by counting bloom filter.");
        }
        UserLoginIdentity identity = userLoginIdentityMapper.findVerifiedByPhone(normalizedPhone);
        if (identity == null || !isActive(identity)) {
            return verifyFail(ERROR_PHONE_LOGIN_DB_MISS, "Phone number was not found in verified login identities.");
        }
        phoneBoundCountingBloomService.addVerifiedPhoneAsync(normalizedPhone);

        SmsCodeSendResult sendResult = smsCodeService.sendBindPhoneCode(dialCode, phoneNumber, clientIp);
        if (!sendResult.isSuccess()) {
            return verifyFail(sendResult.getMessage());
        }
        return LoginVerificationResult.builder()
                .success(true)
                .message(sendResult.getMessage())
                .riskLevel(loginChallengePolicy.normalizeRiskLevel(riskLevel))
                .authenticated(false)
                .build();
    }

    @Override
    @Transactional
    public LoginVerificationResult bindVerifiedPhone(String flowId,
                                                     String preAuthToken,
                                                     String dialCode,
                                                     String phoneNumber,
                                                     String smsCode) {
        LoginFlowValidationResult validationResult = loginFlowSessionService.validate(flowId, preAuthToken);
        if (!validationResult.valid()) {
            return verifyFail("Login session expired, please restart.");
        }
        LoginFlowSession session = validationResult.session();
        if (session.getStep() != LoginFlowStep.ADD_PHONE) {
            return verifyFail("Phone binding is not required for this login.");
        }
        LoginVerificationResult phoneValidationResult = validatePhoneCanBeBound(dialCode, phoneNumber);
        if (phoneValidationResult != null) {
            return phoneValidationResult;
        }
        SmsCodeVerifyResult verifyResult = smsCodeService.verifyBindPhoneCode(dialCode, phoneNumber, smsCode);
        if (!verifyResult.isSuccess()) {
            return verifyFail(verifyResult.getMessage());
        }
        String phone = StrUtil.blankToDefault(verifyResult.getNormalizedE164(), normalizePhone(dialCode, phoneNumber));
        int updatedRows = userLoginIdentityMapper.bindVerifiedPhoneByUserId(session.getUserId(), phone);
        if (updatedRows <= 0) {
            return verifyFail("Failed to bind phone number.");
        }
        phoneBoundCountingBloomService.addVerifiedPhoneAsync(phone);
        userLoginIdentityMapper.updateLastLoginAtByUserId(session.getUserId());
        LoginFlowSession updated = loginFlowSessionService.updateStep(
                session.getFlowId(),
                LoginFlowStep.DONE,
                session.getCompletedFactors(),
                false,
                true
        );
        return fromSession(updated, true, "Login completed.");
    }

    @Override
    public boolean refreshPendingChallengeSelection(String email,
                                                    String deviceFingerprint,
                                                    String expectedChallengeType,
                                                    String expectedChallengeSubType) {
        if (StrUtil.hasBlank(email, deviceFingerprint, expectedChallengeType)) {
            return false;
        }
        ChallengeSelection expected = new ChallengeSelection(
                expectedChallengeType,
                loginChallengePolicy.normalizeExpectedChallengeSubType(expectedChallengeType, expectedChallengeSubType)
        );
        return loginChallengeSessionService.refreshPendingChallengeSelection(email, deviceFingerprint, expected);
    }

    private LoginFlowStartResult requireLoginChallengeIfNeeded(String email,
                                                               String deviceFingerprint,
                                                               String preAuthToken,
                                                               String riskLevel,
                                                               String publicIp,
                                                               String captchaUuid,
                                                               String captchaCode,
                                                               boolean wafResumeRequest) {
        ChallengeSelection pendingSelection = loginChallengeSessionService.readPendingChallengeSelection(email, deviceFingerprint);
        if (pendingSelection != null && loginChallengePolicy.isOperationTimeoutChallenge(pendingSelection.type())) {
            Long waitUntil = loginChallengeSessionService.readOperationTimeoutWaitUntil(email, deviceFingerprint);
            if (waitUntil != null && waitUntil <= System.currentTimeMillis()) {
                loginChallengeSessionService.clearPendingChallengeSelection(email, deviceFingerprint);
                return operationTimeoutResult(riskLevel, 0L, waitUntil, "Operation timeout.");
            }
        }

        ChallengeSelection challengeSelection = pendingSelection != null
                ? pendingSelection
                : loginChallengePolicy.resolveChallengeSelection(riskLevel, email, deviceFingerprint);
        String challengeType = challengeSelection == null ? null : challengeSelection.type();
        String challengeSubType = challengeSelection == null ? null : challengeSelection.subType();
        if (StrUtil.isBlank(challengeType)) {
            loginChallengeSessionService.clearPendingChallengeSelection(email, deviceFingerprint);
            return null;
        }

        if (loginChallengePolicy.isWafChallenge(challengeType)) {
            if (wafResumeRequest && loginChallengeSessionService.consumeWafVerified(preAuthToken)) {
                loginChallengeSessionService.clearPendingChallengeSelection(email, deviceFingerprint);
                return null;
            }
            return LoginFlowStartResult.builder()
                    .success(false)
                    .message("WAF verification is required.")
                    .riskLevel(riskLevel)
                    .challengeType(CHALLENGE_WAF_REQUIRED)
                    .verifyUrl(buildWafVerifyUrl())
                    .build();
        }

        if (loginChallengePolicy.isOperationTimeoutChallenge(challengeType)) {
            loginChallengeSessionService.savePendingChallengeSelection(email, deviceFingerprint, challengeSelection);
            long waitUntil = loginChallengeSessionService.ensureOperationTimeoutWaitUntil(email, deviceFingerprint);
            return operationTimeoutResult(
                    riskLevel,
                    Math.max(0L, waitUntil - System.currentTimeMillis()),
                    waitUntil,
                    "Please retry after the operation timeout window."
            );
        }

        if (loginChallengePolicy.isCaptchaChallenge(challengeType)) {
            boolean hasCaptchaPayload = StrUtil.isNotBlank(captchaUuid) || StrUtil.isNotBlank(captchaCode);
            loginChallengeSessionService.savePendingChallengeSelection(email, deviceFingerprint, challengeSelection);
            if (!hasCaptchaPayload) {
                return captchaRequiredResult(riskLevel, challengeType, challengeSubType);
            }
            if (!verifyRequiredCaptcha(challengeType, publicIp, captchaUuid, captchaCode)) {
                return captchaRequiredResult(riskLevel, challengeType, challengeSubType);
            }
            loginChallengeSessionService.clearPendingChallengeSelection(email, deviceFingerprint);
        }

        return null;
    }

    private LoginVerificationResult completeFactor(LoginFlowSession session,
                                                   LoginFactor factor,
                                                   UserLoginIdentity identity) {
        Set<LoginFactor> completedFactors = new LinkedHashSet<>(session.getCompletedFactors());
        completedFactors.add(factor);

        LoginFlowStep nextStep = resolveNextStep(session, identity, completedFactors);
        boolean authenticated = nextStep == LoginFlowStep.DONE;
        boolean requirePhoneBinding = nextStep == LoginFlowStep.ADD_PHONE;
        if (authenticated) {
            userLoginIdentityMapper.updateLastLoginAtByUserId(identity.getUserId());
        }
        LoginFlowSession updated = loginFlowSessionService.updateStep(
                session.getFlowId(),
                nextStep,
                completedFactors,
                requirePhoneBinding,
                authenticated
        );
        return fromSession(updated, authenticated, authenticated ? "Login completed." : "Verification accepted.");
    }

    private LoginFlowStep resolveNextStep(LoginFlowSession session,
                                          UserLoginIdentity identity,
                                          Set<LoginFactor> completedFactors) {
        if (completedFactors.size() >= session.getRequiredFactorCount()) {
            if (session.isRequirePhoneBinding() && !Boolean.TRUE.equals(identity.getPhoneVerified())) {
                return LoginFlowStep.ADD_PHONE;
            }
            return LoginFlowStep.DONE;
        }
        for (LoginFactor factor : Set.of(LoginFactor.PASSWORD, LoginFactor.EMAIL_OTP, LoginFactor.TOTP)) {
            if (session.getAvailableFactors().contains(factor) && !completedFactors.contains(factor)) {
                return stepForFactor(factor);
            }
        }
        return LoginFlowStep.DONE;
    }

    private boolean canUseFactor(LoginFlowSession session, LoginFactor factor) {
        if (session == null || factor == null) {
            return false;
        }
        if (session.isCompleted()
                || session.getStep() == LoginFlowStep.DONE
                || session.getStep() == LoginFlowStep.ADD_PHONE
                || session.getStep() == LoginFlowStep.BLOCKED
                || session.getStep() == LoginFlowStep.OPERATION_TIMEOUT) {
            return false;
        }
        return session.getAvailableFactors().contains(factor)
                && !session.getCompletedFactors().contains(factor);
    }

    private LoginVerificationResult validatePhoneCanBeBound(String dialCode, String phoneNumber) {
        PhoneNumberValidationService.ValidationResult validationResult =
                phoneNumberValidationService.validateMobileLikeNumber(dialCode, phoneNumber);
        if (!validationResult.allowed() || StrUtil.isBlank(validationResult.normalizedE164())) {
            return verifyFail("Phone number validation failed.");
        }
        PhoneBoundCountingBloomService.PhoneBoundLookupResult lookupResult =
                phoneBoundCountingBloomService.lookupVerifiedPhone(validationResult.normalizedE164());
        if (!lookupResult.available()) {
            return verifyFail(ERROR_PHONE_BOUND_BLOOM_UNAVAILABLE, "Phone existence filter is temporarily unavailable.");
        }
        if (lookupResult.mightContain()) {
            return verifyFail(ERROR_PHONE_ALREADY_BOUND, "This phone number is already in use.");
        }
        return null;
    }

    private LoginVerificationResult fromSmsRiskGate(PhoneSmsRiskGateResult gateResult) {
        return LoginVerificationResult.builder()
                .success(false)
                .error(gateResult.getError())
                .message(gateResult.getMessage())
                .email(gateResult.getChallengeIdentity())
                .riskLevel(gateResult.getRiskLevel())
                .challengeType(gateResult.getChallengeType())
                .challengeSubType(gateResult.getChallengeSubType())
                .challengeSiteKey(gateResult.getChallengeSiteKey())
                .authenticated(false)
                .build();
    }

    private String normalizeVerifiedPhone(String dialCode, String phoneNumber) {
        PhoneNumberValidationService.ValidationResult validationResult =
                phoneNumberValidationService.validateMobileLikeNumber(dialCode, phoneNumber);
        if (validationResult.allowed() && StrUtil.isNotBlank(validationResult.normalizedE164())) {
            return validationResult.normalizedE164();
        }
        return normalizePhone(dialCode, phoneNumber);
    }

    private ResolvedLoginSession resolveSessionForFactor(LoginFlowSession session, LoginFactor factor) {
        if (!canUseFactor(session, factor)) {
            return null;
        }

        UserLoginIdentity identity = userLoginIdentityMapper.findByEmail(session.getEmail());
        if (identity == null || !isActive(identity)) {
            return null;
        }

        Set<LoginFactor> availableFactors = resolveAvailableFactors(identity);
        if (availableFactors.isEmpty() || !availableFactors.contains(factor)) {
            return null;
        }

        int requiredFactorCount = resolveRequiredFactorCount(session.getRiskLevel(), availableFactors);
        boolean requirePhoneBinding = loginChallengePolicy.shouldRequirePhoneBinding(session.getRiskLevel())
                && !Boolean.TRUE.equals(identity.getPhoneVerified());
        LoginFlowSession resolvedSession = loginFlowSessionService.resolveIdentity(
                session.getFlowId(),
                identity.getUserId(),
                availableFactors,
                requiredFactorCount,
                requirePhoneBinding
        );
        if (resolvedSession == null || !canUseFactor(resolvedSession, factor)) {
            return null;
        }
        return new ResolvedLoginSession(resolvedSession, identity);
    }

    private Set<LoginFactor> candidateLoginFactors() {
        Set<LoginFactor> factors = new LinkedHashSet<>();
        factors.add(LoginFactor.PASSWORD);
        factors.add(LoginFactor.EMAIL_OTP);
        factors.add(LoginFactor.TOTP);
        return factors;
    }

    private Set<LoginFactor> resolveAvailableFactors(UserLoginIdentity identity) {
        Set<LoginFactor> factors = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(identity.getEmailPasswordHash())) {
            factors.add(LoginFactor.PASSWORD);
        }
        if (StrUtil.isNotBlank(identity.getEmail()) && Boolean.TRUE.equals(identity.getEmailVerified())) {
            factors.add(LoginFactor.EMAIL_OTP);
        }
        if (Boolean.TRUE.equals(identity.getTotpEnabled()) && StrUtil.isNotBlank(identity.getTotpSecretEncrypted())) {
            factors.add(LoginFactor.TOTP);
        }
        return factors;
    }

    private int resolveRequiredFactorCount(String riskLevel, Set<LoginFactor> availableFactors) {
        if (loginChallengePolicy.shouldRequireTwoFactors(riskLevel)) {
            return Math.max(1, Math.min(2, availableFactors.size()));
        }
        return 1;
    }

    private LoginFlowStep firstStepForAvailableFactors(Set<LoginFactor> availableFactors) {
        if (availableFactors.contains(LoginFactor.PASSWORD)) {
            return LoginFlowStep.PASSWORD;
        }
        if (availableFactors.contains(LoginFactor.EMAIL_OTP)) {
            return LoginFlowStep.EMAIL_VERIFICATION;
        }
        if (availableFactors.contains(LoginFactor.TOTP)) {
            return LoginFlowStep.TOTP_VERIFICATION;
        }
        return LoginFlowStep.PASSWORD;
    }

    private LoginFlowStep stepForFactor(LoginFactor factor) {
        return switch (factor) {
            case PASSWORD -> LoginFlowStep.PASSWORD;
            case EMAIL_OTP -> LoginFlowStep.EMAIL_VERIFICATION;
            case TOTP -> LoginFlowStep.TOTP_VERIFICATION;
        };
    }

    private String pathForStep(LoginFlowStep step) {
        if (step == null) {
            return LOGIN_PATH;
        }
        return switch (step) {
            case PASSWORD -> PASSWORD_PATH;
            case EMAIL_VERIFICATION -> LOGIN_EMAIL_VERIFICATION_PATH;
            case TOTP_VERIFICATION -> LOGIN_TOTP_VERIFICATION_PATH;
            case ADD_PHONE -> LOGIN_ADD_PHONE_PATH;
            case DONE -> AUTHENTICATED_PATH;
            case BLOCKED, OPERATION_TIMEOUT -> LOGIN_PATH;
        };
    }

    private boolean verifyRequiredCaptcha(String challengeType,
                                          String publicIp,
                                          String captchaUuid,
                                          String captchaCode) {
        return switch (challengeType) {
            case CHALLENGE_HUTOOL_SHEAR -> hutoolCaptchaService.validateCaptcha(LOGIN_CAPTCHA_TYPE, captchaUuid, captchaCode);
            case CHALLENGE_TIANAI -> tianaiCaptchaService.validateCaptcha(captchaUuid, captchaCode);
            case CHALLENGE_CLOUDFLARE_TURNSTILE -> thirdPartyCaptchaService.validateTurnstile(captchaCode, publicIp);
            case CHALLENGE_HCAPTCHA -> thirdPartyCaptchaService.validateHCaptcha(captchaCode, publicIp);
            case CHALLENGE_GOOGLE_RECAPTCHA_V2, CHALLENGE_GOOGLE_RECAPTCHA_V3_LEGACY ->
                    thirdPartyCaptchaService.validateRecaptcha(captchaCode, publicIp);
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

    private LoginFlowStartResult captchaRequiredResult(String riskLevel, String challengeType, String challengeSubType) {
        return LoginFlowStartResult.builder()
                .success(false)
                .message("Security verification is required before continuing.")
                .riskLevel(riskLevel)
                .challengeType(challengeType)
                .challengeSubType(challengeSubType)
                .challengeSiteKey(resolveChallengeSiteKey(challengeType))
                .build();
    }

    private LoginFlowStartResult operationTimeoutResult(String riskLevel,
                                                        Long retryAfterMs,
                                                        Long waitUntilEpochMs,
                                                        String message) {
        return LoginFlowStartResult.builder()
                .success(false)
                .message(message)
                .riskLevel(riskLevel)
                .challengeType(CHALLENGE_OPERATION_TIMEOUT)
                .retryAfterMs(retryAfterMs)
                .waitUntilEpochMs(waitUntilEpochMs)
                .build();
    }

    private LoginFlowStartResult startFail(String message) {
        return LoginFlowStartResult.builder()
                .success(false)
                .message(message)
                .build();
    }

    private LoginVerificationResult fromSession(LoginFlowSession session, boolean authenticated, String message) {
        if (session == null) {
            return verifyFail("Login session expired, please restart.");
        }
        return LoginVerificationResult.builder()
                .success(true)
                .message(message)
                .flowId(session.getFlowId())
                .userId(session.getUserId())
                .step(session.getStep())
                .redirectPath(pathForStep(session.getStep()))
                .availableFactors(session.getAvailableFactors())
                .completedFactors(session.getCompletedFactors())
                .requiredFactorCount(session.getRequiredFactorCount())
                .requirePhoneBinding(session.isRequirePhoneBinding())
                .authenticated(authenticated)
                .build();
    }

    private LoginVerificationResult verifyFail(String message) {
        return LoginVerificationResult.builder()
                .success(false)
                .message(message)
                .authenticated(false)
                .build();
    }

    private LoginVerificationResult verifyFail(String error, String message) {
        return LoginVerificationResult.builder()
                .success(false)
                .error(error)
                .message(message)
                .authenticated(false)
                .build();
    }

    private LoginVerificationResult invalidState() {
        return LoginVerificationResult.builder()
                .success(false)
                .error(ERROR_INVALID_STATE)
                .message(INVALID_STATE_MESSAGE)
                .redirectPath(SESSION_ENDED_PATH)
                .authenticated(false)
                .build();
    }

    private UserLoginIdentity requireIdentity(LoginFlowSession session) {
        UserLoginIdentity identity = userLoginIdentityMapper.findByUserId(session.getUserId());
        if (identity == null) {
            throw new IllegalStateException("Login identity not found.");
        }
        return identity;
    }

    private boolean isActive(UserLoginIdentity identity) {
        String status = normalizeText(identity.getStatus());
        return status == null || "ACTIVE".equalsIgnoreCase(status);
    }

    private String emailCodeKey(String flowId) {
        return LoginRedisKeys.EMAIL_CODE_PREFIX + flowId;
    }

    private String buildWafVerifyUrl() {
        return "/shopping/auth/waf/verify?return="
                + URLEncoder.encode(PASSWORD_PATH, StandardCharsets.UTF_8);
    }

    private String normalizePhone(String dialCode, String phoneNumber) {
        String normalizedDialCode = StrUtil.blankToDefault(dialCode, "").trim();
        String normalizedPhoneNumber = StrUtil.blankToDefault(phoneNumber, "").trim();
        return (normalizedDialCode + normalizedPhoneNumber).replace(" ", "");
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

    private record ResolvedLoginSession(LoginFlowSession session, UserLoginIdentity identity) {
    }
}
