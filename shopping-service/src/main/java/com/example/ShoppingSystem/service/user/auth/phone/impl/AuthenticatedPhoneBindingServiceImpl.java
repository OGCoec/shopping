package com.example.ShoppingSystem.service.user.auth.phone.impl;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.phone.PhoneNumberValidationService;
import com.example.ShoppingSystem.service.user.auth.phone.AuthenticatedPhoneBindingService;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBindingAvailabilityService;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBindingWriteService;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneVerifiedUserLookupService;
import com.example.ShoppingSystem.service.user.auth.phone.model.AuthenticatedPhoneBindingResult;
import com.example.ShoppingSystem.service.user.auth.sms.PhoneSmsRiskGateService;
import com.example.ShoppingSystem.service.user.auth.sms.SmsCodeService;
import com.example.ShoppingSystem.service.user.auth.sms.model.PhoneSmsRiskGateResult;
import com.example.ShoppingSystem.service.user.auth.sms.model.SmsCodeSendResult;
import com.example.ShoppingSystem.service.user.auth.sms.model.SmsCodeVerifyResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedPhoneBindingServiceImpl implements AuthenticatedPhoneBindingService {

    private static final String ERROR_AUTH_REQUIRED = "AUTH_REQUIRED";
    private static final String SECURITY_PHONE_REDIRECT_PATH = "/shopping/user/security/phone";
    private static final String SECURITY_PHONE_COMPLETED_REDIRECT_PATH = "/shopping/user/console";

    private final PhoneNumberValidationService phoneNumberValidationService;
    private final PhoneBindingAvailabilityService phoneBindingAvailabilityService;
    private final PhoneBindingWriteService phoneBindingWriteService;
    private final PhoneVerifiedUserLookupService phoneVerifiedUserLookupService;
    private final PhoneSmsRiskGateService phoneSmsRiskGateService;
    private final SmsCodeService smsCodeService;

    public AuthenticatedPhoneBindingServiceImpl(PhoneNumberValidationService phoneNumberValidationService,
                                                PhoneBindingAvailabilityService phoneBindingAvailabilityService,
                                                PhoneBindingWriteService phoneBindingWriteService,
                                                PhoneVerifiedUserLookupService phoneVerifiedUserLookupService,
                                                PhoneSmsRiskGateService phoneSmsRiskGateService,
                                                SmsCodeService smsCodeService) {
        this.phoneNumberValidationService = phoneNumberValidationService;
        this.phoneBindingAvailabilityService = phoneBindingAvailabilityService;
        this.phoneBindingWriteService = phoneBindingWriteService;
        this.phoneVerifiedUserLookupService = phoneVerifiedUserLookupService;
        this.phoneSmsRiskGateService = phoneSmsRiskGateService;
        this.smsCodeService = smsCodeService;
    }

    @Override
    @Transactional
    public AuthenticatedPhoneBindingResult sendPhoneBindCode(Long userId,
                                                             String preAuthToken,
                                                             String dialCode,
                                                             String phoneNumber,
                                                             String clientIp,
                                                             String riskLevel,
                                                             String deviceFingerprint,
                                                             String captchaUuid,
                                                             String captchaCode) {
        if (userId == null) {
            return fail(ERROR_AUTH_REQUIRED, "Authentication is required.");
        }
        if (phoneVerifiedUserLookupService.isPhoneVerified(userId)) {
            return alreadyVerified(userId, riskLevel);
        }

        AuthenticatedPhoneBindingResult phoneValidationResult = validatePhoneCanBeBound(dialCode, phoneNumber);
        if (phoneValidationResult != null) {
            return phoneValidationResult;
        }

        String normalizedPhone = normalizeVerifiedPhone(dialCode, phoneNumber);
        String normalizedRiskLevel = normalizeRiskLevel(riskLevel);
        PhoneSmsRiskGateResult gateResult = phoneSmsRiskGateService.checkOrVerify(
                PhoneSmsRiskGateService.SCENE_BIND_PHONE_SMS,
                normalizedPhone,
                preAuthToken,
                deviceFingerprint,
                normalizedRiskLevel,
                clientIp,
                captchaUuid,
                captchaCode
        );
        if (!gateResult.isAllowed()) {
            return fromSmsRiskGate(gateResult, userId);
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
        return AuthenticatedPhoneBindingResult.builder()
                .success(true)
                .message(sendResult.getMessage())
                .normalizedE164(sendResult.getNormalizedE164())
                .userId(userId)
                .riskLevel(normalizedRiskLevel)
                .redirectPath(SECURITY_PHONE_REDIRECT_PATH)
                .requirePhoneBinding(true)
                .authenticated(true)
                .retryAfterMs(sendResult.getRetryAfterMs())
                .build();
    }

    @Override
    @Transactional
    public AuthenticatedPhoneBindingResult bindVerifiedPhone(Long userId,
                                                             String dialCode,
                                                             String phoneNumber,
                                                             String smsCode) {
        if (userId == null) {
            return fail(ERROR_AUTH_REQUIRED, "Authentication is required.");
        }
        if (phoneVerifiedUserLookupService.isPhoneVerified(userId)) {
            return alreadyVerified(userId, null);
        }

        AuthenticatedPhoneBindingResult phoneValidationResult = validatePhoneCanBeBound(dialCode, phoneNumber);
        if (phoneValidationResult != null) {
            return phoneValidationResult;
        }

        SmsCodeVerifyResult verifyResult = smsCodeService.verifyBindPhoneCode(dialCode, phoneNumber, smsCode);
        if (!verifyResult.isSuccess()) {
            return fail(verifyResult.getReasonCode(), verifyResult.getReasonCode(), verifyResult.getMessage());
        }

        String phone = StrUtil.blankToDefault(verifyResult.getNormalizedE164(), normalizeVerifiedPhone(dialCode, phoneNumber));
        PhoneBindingWriteService.PhoneBindingResult bindingResult =
                phoneBindingWriteService.bindVerifiedPhone(userId, phone);
        if (!bindingResult.success()) {
            return fail(bindingResult.errorCode(), bindingResult.reasonCode(), bindingResult.message(), null, phone);
        }

        return AuthenticatedPhoneBindingResult.builder()
                .success(true)
                .message("Phone verification completed.")
                .normalizedE164(phone)
                .userId(userId)
                .redirectPath(SECURITY_PHONE_COMPLETED_REDIRECT_PATH)
                .requirePhoneBinding(false)
                .authenticated(true)
                .build();
    }

    private AuthenticatedPhoneBindingResult validatePhoneCanBeBound(String dialCode, String phoneNumber) {
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

    private AuthenticatedPhoneBindingResult fromSmsRiskGate(PhoneSmsRiskGateResult gateResult, Long userId) {
        return AuthenticatedPhoneBindingResult.builder()
                .success(false)
                .error(gateResult.getError())
                .reasonCode(gateResult.getError())
                .message(gateResult.getMessage())
                .userId(userId)
                .riskLevel(gateResult.getRiskLevel())
                .redirectPath(SECURITY_PHONE_REDIRECT_PATH)
                .requirePhoneBinding(true)
                .authenticated(true)
                .challengeType(gateResult.getChallengeType())
                .challengeSubType(gateResult.getChallengeSubType())
                .challengeSiteKey(gateResult.getChallengeSiteKey())
                .build();
    }

    private AuthenticatedPhoneBindingResult alreadyVerified(Long userId, String riskLevel) {
        return AuthenticatedPhoneBindingResult.builder()
                .success(true)
                .message("Phone number is already verified.")
                .userId(userId)
                .riskLevel(normalizeRiskLevel(riskLevel))
                .redirectPath(SECURITY_PHONE_COMPLETED_REDIRECT_PATH)
                .requirePhoneBinding(false)
                .authenticated(true)
                .build();
    }

    private String normalizeVerifiedPhone(String dialCode, String phoneNumber) {
        PhoneNumberValidationService.ValidationResult validationResult =
                phoneNumberValidationService.validateMobileLikeNumber(dialCode, phoneNumber);
        if (validationResult.allowed() && StrUtil.isNotBlank(validationResult.normalizedE164())) {
            return validationResult.normalizedE164();
        }
        return StrUtil.blankToDefault(dialCode, "").trim()
                + StrUtil.blankToDefault(phoneNumber, "").trim();
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = StrUtil.blankToDefault(riskLevel, "").trim().toUpperCase();
        return switch (normalized) {
            case "L1", "L2", "L3", "L4", "L5", "L6" -> normalized;
            default -> "L1";
        };
    }

    private AuthenticatedPhoneBindingResult fail(String error, String message) {
        return fail(error, error, message);
    }

    private AuthenticatedPhoneBindingResult fail(String error, String reasonCode, String message) {
        return fail(error, reasonCode, message, null, null);
    }

    private AuthenticatedPhoneBindingResult fail(String error, String reasonCode, String message, String phoneType, String normalizedE164) {
        return fail(error, reasonCode, message, phoneType, normalizedE164, null);
    }

    private AuthenticatedPhoneBindingResult fail(String error,
                                                 String reasonCode,
                                                 String message,
                                                 String phoneType,
                                                 String normalizedE164,
                                                 Long retryAfterMs) {
        return AuthenticatedPhoneBindingResult.builder()
                .success(false)
                .error(error)
                .reasonCode(reasonCode)
                .message(message)
                .phoneType(phoneType)
                .normalizedE164(normalizedE164)
                .redirectPath(SECURITY_PHONE_REDIRECT_PATH)
                .requirePhoneBinding(true)
                .authenticated(true)
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
