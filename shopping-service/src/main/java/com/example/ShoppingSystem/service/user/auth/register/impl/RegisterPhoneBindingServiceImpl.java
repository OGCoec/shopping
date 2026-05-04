package com.example.ShoppingSystem.service.user.auth.register.impl;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.phone.PhoneNumberValidationService;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBoundCountingBloomService;
import com.example.ShoppingSystem.service.user.auth.register.RegisterFlowSessionService;
import com.example.ShoppingSystem.service.user.auth.register.RegisterPhoneBindingService;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowSession;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowStep;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterFlowValidationResult;
import com.example.ShoppingSystem.service.user.auth.register.model.RegisterPhoneBindingResult;
import com.example.ShoppingSystem.service.user.auth.sms.PhoneSmsRiskGateService;
import com.example.ShoppingSystem.service.user.auth.sms.SmsCodeService;
import com.example.ShoppingSystem.service.user.auth.sms.model.PhoneSmsRiskGateResult;
import com.example.ShoppingSystem.service.user.auth.sms.model.SmsCodeSendResult;
import com.example.ShoppingSystem.service.user.auth.sms.model.SmsCodeVerifyResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterPhoneBindingServiceImpl implements RegisterPhoneBindingService {

    private static final String ERROR_PHONE_ALREADY_BOUND = "PHONE_ALREADY_BOUND";
    private static final String ERROR_PHONE_BOUND_BLOOM_UNAVAILABLE = "PHONE_BOUND_BLOOM_UNAVAILABLE";
    private static final String REGISTER_COMPLETED_REDIRECT_PATH = "/shopping/user/log-in?register_notice=register-completed";

    private final RegisterFlowSessionService registerFlowSessionService;
    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final PhoneNumberValidationService phoneNumberValidationService;
    private final PhoneBoundCountingBloomService phoneBoundCountingBloomService;
    private final PhoneSmsRiskGateService phoneSmsRiskGateService;
    private final SmsCodeService smsCodeService;

    public RegisterPhoneBindingServiceImpl(RegisterFlowSessionService registerFlowSessionService,
                                           UserLoginIdentityMapper userLoginIdentityMapper,
                                           PhoneNumberValidationService phoneNumberValidationService,
                                           PhoneBoundCountingBloomService phoneBoundCountingBloomService,
                                           PhoneSmsRiskGateService phoneSmsRiskGateService,
                                           SmsCodeService smsCodeService) {
        this.registerFlowSessionService = registerFlowSessionService;
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.phoneNumberValidationService = phoneNumberValidationService;
        this.phoneBoundCountingBloomService = phoneBoundCountingBloomService;
        this.phoneSmsRiskGateService = phoneSmsRiskGateService;
        this.smsCodeService = smsCodeService;
    }

    @Override
    @Transactional
    public RegisterPhoneBindingResult sendPhoneBindCode(String flowId,
                                                        String preAuthToken,
                                                        String dialCode,
                                                        String phoneNumber,
                                                        String clientIp,
                                                        String riskLevel,
                                                        String deviceFingerprint,
                                                        String captchaUuid,
                                                        String captchaCode) {
        RegisterFlowSession session = requireAddPhoneSession(flowId, preAuthToken);
        if (session == null) {
            return fail("REGISTER_SESSION_EXPIRED", "Register session expired, please restart.");
        }

        RegisterPhoneBindingResult phoneValidationResult = validatePhoneCanBeBound(dialCode, phoneNumber);
        if (phoneValidationResult != null) {
            return phoneValidationResult;
        }

        String normalizedPhone = normalizeVerifiedPhone(dialCode, phoneNumber);
        String effectiveRiskLevel = effectiveRiskLevel(session, riskLevel);
        PhoneSmsRiskGateResult gateResult = phoneSmsRiskGateService.checkOrVerify(
                PhoneSmsRiskGateService.SCENE_BIND_PHONE_SMS,
                normalizedPhone,
                preAuthToken,
                deviceFingerprint,
                effectiveRiskLevel,
                clientIp,
                captchaUuid,
                captchaCode
        );
        if (!gateResult.isAllowed()) {
            return fromSmsRiskGate(gateResult, session);
        }

        SmsCodeSendResult sendResult = smsCodeService.sendBindPhoneCode(dialCode, phoneNumber, clientIp);
        if (!sendResult.isSuccess()) {
            return fail(sendResult.getReasonCode(), sendResult.getReasonCode(), sendResult.getMessage(), null, sendResult.getNormalizedE164());
        }
        return RegisterPhoneBindingResult.builder()
                .success(true)
                .message(sendResult.getMessage())
                .normalizedE164(sendResult.getNormalizedE164())
                .email(session.getEmail())
                .riskLevel(effectiveRiskLevel)
                .step(RegisterFlowStep.ADD_PHONE.name())
                .requirePhoneBinding(true)
                .authenticated(false)
                .build();
    }

    @Override
    @Transactional
    public RegisterPhoneBindingResult bindVerifiedPhone(String flowId,
                                                        String preAuthToken,
                                                        String dialCode,
                                                        String phoneNumber,
                                                        String smsCode) {
        RegisterFlowSession session = requireAddPhoneSession(flowId, preAuthToken);
        if (session == null) {
            return fail("REGISTER_SESSION_EXPIRED", "Register session expired, please restart.");
        }

        UserLoginIdentity identity = userLoginIdentityMapper.findByEmail(session.getEmail());
        if (identity == null || identity.getUserId() == null) {
            return fail("REGISTER_IDENTITY_MISSING", "Registered account identity was not found.");
        }

        RegisterPhoneBindingResult phoneValidationResult = validatePhoneCanBeBound(dialCode, phoneNumber);
        if (phoneValidationResult != null) {
            return phoneValidationResult;
        }

        SmsCodeVerifyResult verifyResult = smsCodeService.verifyBindPhoneCode(dialCode, phoneNumber, smsCode);
        if (!verifyResult.isSuccess()) {
            return fail(verifyResult.getReasonCode(), verifyResult.getReasonCode(), verifyResult.getMessage(), null, verifyResult.getNormalizedE164());
        }

        String phone = StrUtil.blankToDefault(verifyResult.getNormalizedE164(), normalizeVerifiedPhone(dialCode, phoneNumber));
        int updatedRows = userLoginIdentityMapper.bindVerifiedPhoneByUserId(identity.getUserId(), phone);
        if (updatedRows <= 0) {
            return fail("Failed to bind phone number.");
        }

        phoneBoundCountingBloomService.addVerifiedPhoneAsync(phone);
        registerFlowSessionService.updateStep(
                session.getFlowId(),
                RegisterFlowStep.DONE,
                session.getRiskLevel(),
                false,
                true
        );
        return RegisterPhoneBindingResult.builder()
                .success(true)
                .message("Register completed.")
                .normalizedE164(phone)
                .userId(identity.getUserId())
                .email(session.getEmail())
                .riskLevel(effectiveRiskLevel(session, null))
                .step(RegisterFlowStep.DONE.name())
                .redirectPath(REGISTER_COMPLETED_REDIRECT_PATH)
                .requirePhoneBinding(false)
                .authenticated(false)
                .build();
    }

    private RegisterFlowSession requireAddPhoneSession(String flowId, String preAuthToken) {
        RegisterFlowValidationResult validationResult = registerFlowSessionService.validate(flowId, preAuthToken);
        if (!validationResult.valid()) {
            return null;
        }
        RegisterFlowSession session = validationResult.session();
        if (session == null
                || session.isCompleted()
                || session.getStep() != RegisterFlowStep.ADD_PHONE
                || !session.isRequirePhoneBinding()) {
            return null;
        }
        return session;
    }

    private RegisterPhoneBindingResult validatePhoneCanBeBound(String dialCode, String phoneNumber) {
        PhoneNumberValidationService.ValidationResult validationResult =
                phoneNumberValidationService.validateMobileLikeNumber(dialCode, phoneNumber);
        if (!validationResult.allowed() || StrUtil.isBlank(validationResult.normalizedE164())) {
            return fail(
                    validationResult.reasonCode(),
                    validationResult.reasonCode(),
                    resolvePhoneValidationMessage(validationResult.reasonCode()),
                    validationResult.phoneType(),
                    validationResult.normalizedE164()
            );
        }
        PhoneBoundCountingBloomService.PhoneBoundLookupResult lookupResult =
                phoneBoundCountingBloomService.lookupVerifiedPhone(validationResult.normalizedE164());
        if (!lookupResult.available()) {
            return fail(ERROR_PHONE_BOUND_BLOOM_UNAVAILABLE, ERROR_PHONE_BOUND_BLOOM_UNAVAILABLE, "Phone existence filter is temporarily unavailable.");
        }
        if (lookupResult.mightContain()) {
            return fail(ERROR_PHONE_ALREADY_BOUND, ERROR_PHONE_ALREADY_BOUND, "This phone number is already in use.");
        }
        return null;
    }

    private RegisterPhoneBindingResult fromSmsRiskGate(PhoneSmsRiskGateResult gateResult, RegisterFlowSession session) {
        return RegisterPhoneBindingResult.builder()
                .success(false)
                .error(gateResult.getError())
                .reasonCode(gateResult.getError())
                .message(gateResult.getMessage())
                .email(gateResult.getChallengeIdentity())
                .riskLevel(gateResult.getRiskLevel())
                .step(session == null || session.getStep() == null ? null : session.getStep().name())
                .requirePhoneBinding(true)
                .authenticated(false)
                .challengeType(gateResult.getChallengeType())
                .challengeSubType(gateResult.getChallengeSubType())
                .challengeSiteKey(gateResult.getChallengeSiteKey())
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

    private String normalizePhone(String dialCode, String phoneNumber) {
        return StrUtil.blankToDefault(dialCode, "").trim()
                + StrUtil.blankToDefault(phoneNumber, "").trim();
    }

    private String effectiveRiskLevel(RegisterFlowSession session, String fallbackRiskLevel) {
        String sessionRiskLevel = session == null ? "" : StrUtil.blankToDefault(session.getRiskLevel(), "").trim().toUpperCase();
        if (StrUtil.isNotBlank(sessionRiskLevel)) {
            return sessionRiskLevel;
        }
        String fallback = StrUtil.blankToDefault(fallbackRiskLevel, "").trim().toUpperCase();
        return StrUtil.blankToDefault(fallback, "L1");
    }

    private RegisterPhoneBindingResult fail(String message) {
        return RegisterPhoneBindingResult.builder()
                .success(false)
                .message(message)
                .authenticated(false)
                .build();
    }

    private RegisterPhoneBindingResult fail(String error, String message) {
        return fail(error, error, message);
    }

    private RegisterPhoneBindingResult fail(String error, String reasonCode, String message) {
        return fail(error, reasonCode, message, null, null);
    }

    private RegisterPhoneBindingResult fail(String error, String reasonCode, String message, String phoneType, String normalizedE164) {
        return RegisterPhoneBindingResult.builder()
                .success(false)
                .error(error)
                .reasonCode(reasonCode)
                .message(message)
                .phoneType(phoneType)
                .normalizedE164(normalizedE164)
                .authenticated(false)
                .build();
    }

    private String resolvePhoneValidationMessage(String reasonCode) {
        if (PhoneNumberValidationService.REASON_INVALID_DIAL_CODE.equals(reasonCode)) {
            return "Please choose a valid country or region.";
        }
        if (PhoneNumberValidationService.REASON_VOIP_NOT_ALLOWED.equals(reasonCode)) {
            return "Virtual or VoIP phone numbers are not allowed.";
        }
        if (PhoneNumberValidationService.REASON_FIXED_LINE_NOT_ALLOWED.equals(reasonCode)) {
            return "Landline phone numbers are not allowed.";
        }
        if (PhoneNumberValidationService.REASON_TYPE_NOT_ALLOWED.equals(reasonCode)) {
            return "Only mobile phone numbers are allowed.";
        }
        return "Please enter a valid mobile phone number.";
    }
}
