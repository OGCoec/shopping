package com.example.ShoppingSystem.service.user.auth.register.impl;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.phone.PhoneNumberValidationService;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBindingAvailabilityService;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBindingWriteService;
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

    private static final String REGISTER_COMPLETED_REDIRECT_PATH = "/shopping/user/console";

    private final RegisterFlowSessionService registerFlowSessionService;
    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final PhoneNumberValidationService phoneNumberValidationService;
    private final PhoneBindingAvailabilityService phoneBindingAvailabilityService;
    private final PhoneBindingWriteService phoneBindingWriteService;
    private final PhoneSmsRiskGateService phoneSmsRiskGateService;
    private final SmsCodeService smsCodeService;

    public RegisterPhoneBindingServiceImpl(RegisterFlowSessionService registerFlowSessionService,
                                           UserLoginIdentityMapper userLoginIdentityMapper,
                                           PhoneNumberValidationService phoneNumberValidationService,
                                           PhoneBindingAvailabilityService phoneBindingAvailabilityService,
                                           PhoneBindingWriteService phoneBindingWriteService,
                                           PhoneSmsRiskGateService phoneSmsRiskGateService,
                                           SmsCodeService smsCodeService) {
        this.registerFlowSessionService = registerFlowSessionService;
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.phoneNumberValidationService = phoneNumberValidationService;
        this.phoneBindingAvailabilityService = phoneBindingAvailabilityService;
        this.phoneBindingWriteService = phoneBindingWriteService;
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
            return fail(
                    sendResult.getReasonCode(),
                    sendResult.getReasonCode(),
                    sendResult.getMessage(),
                    null,
                    sendResult.getNormalizedE164(),
                    sendResult.getRetryAfterMs()
            );
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
                .retryAfterMs(sendResult.getRetryAfterMs())
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
        PhoneBindingWriteService.PhoneBindingResult bindingResult =
                phoneBindingWriteService.bindVerifiedPhone(identity.getUserId(), phone);
        if (!bindingResult.success()) {
            return fail(bindingResult.errorCode(), bindingResult.reasonCode(), bindingResult.message(), null, phone);
        }

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
                .authenticated(true)
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
        PhoneBindingAvailabilityService.PhoneBindingAvailability availability =
                phoneBindingAvailabilityService.checkPhoneAvailable(validationResult.normalizedE164());
        if (!availability.allowed()) {
            return fail(availability.errorCode(), availability.reasonCode(), availability.message(),
                    validationResult.phoneType(), validationResult.normalizedE164());
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
        String sessionRiskLevel = normalizeRiskLevel(session == null ? "" : session.getRiskLevel());
        String fallback = normalizeRiskLevel(fallbackRiskLevel);
        return riskRank(fallback) > riskRank(sessionRiskLevel) ? fallback : sessionRiskLevel;
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = StrUtil.blankToDefault(riskLevel, "").trim().toUpperCase();
        return switch (normalized) {
            case "L1", "L2", "L3", "L4", "L5", "L6" -> normalized;
            default -> "L1";
        };
    }

    private int riskRank(String riskLevel) {
        return switch (normalizeRiskLevel(riskLevel)) {
            case "L1" -> 1;
            case "L2" -> 2;
            case "L3" -> 3;
            case "L4" -> 4;
            case "L5" -> 5;
            case "L6" -> 6;
            default -> 0;
        };
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
        return fail(error, reasonCode, message, phoneType, normalizedE164, null);
    }

    private RegisterPhoneBindingResult fail(String error,
                                            String reasonCode,
                                            String message,
                                            String phoneType,
                                            String normalizedE164,
                                            Long retryAfterMs) {
        return RegisterPhoneBindingResult.builder()
                .success(false)
                .error(error)
                .reasonCode(reasonCode)
                .message(message)
                .phoneType(phoneType)
                .normalizedE164(normalizedE164)
                .authenticated(false)
                .retryAfterMs(retryAfterMs)
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
