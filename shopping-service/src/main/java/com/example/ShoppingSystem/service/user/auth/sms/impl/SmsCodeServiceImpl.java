package com.example.ShoppingSystem.service.user.auth.sms.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.config.SmsCodeProperties;
import com.example.ShoppingSystem.phone.PhoneNumberValidationService;
import com.example.ShoppingSystem.service.user.auth.sms.SmsCodeMessagePublisher;
import com.example.ShoppingSystem.service.user.auth.sms.SmsCodeService;
import com.example.ShoppingSystem.service.user.auth.sms.model.SmsCodeSendResult;
import com.example.ShoppingSystem.service.user.auth.sms.model.SmsCodeVerifyResult;
import com.example.ShoppingSystem.service.user.auth.sms.mq.SmsCodeMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Service
public class SmsCodeServiceImpl implements SmsCodeService {

    private static final int MESSAGE_ID_LENGTH = 48;
    private static final String SCENE_BIND_PHONE = "BIND_PHONE";
    private static final String CODE_PREFIX = "auth:sms:bind-phone:code:";
    private static final String COOLDOWN_PREFIX = "auth:sms:bind-phone:cooldown:";
    private static final String DAILY_PREFIX = "auth:sms:bind-phone:daily:";
    private static final String IP_PREFIX = "auth:sms:bind-phone:ip:";
    private static final String REASON_OK = "OK";
    private static final String REASON_RATE_LIMITED = "SMS_RATE_LIMITED";
    private static final String REASON_TEMPLATE_MISSING = "SMS_TEMPLATE_MISSING";
    private static final String REASON_UNSUPPORTED_COUNTRY = "SMS_UNSUPPORTED_COUNTRY";
    private static final String REASON_CODE_INVALID = "SMS_CODE_INVALID";

    private final PhoneNumberValidationService phoneNumberValidationService;
    private final StringRedisTemplate stringRedisTemplate;
    private final SmsCodeMessagePublisher smsCodeMessagePublisher;
    private final SmsCodeProperties properties;

    public SmsCodeServiceImpl(PhoneNumberValidationService phoneNumberValidationService,
                              StringRedisTemplate stringRedisTemplate,
                              SmsCodeMessagePublisher smsCodeMessagePublisher,
                              SmsCodeProperties properties) {
        this.phoneNumberValidationService = phoneNumberValidationService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.smsCodeMessagePublisher = smsCodeMessagePublisher;
        this.properties = properties;
    }

    @Override
    public SmsCodeSendResult sendBindPhoneCode(String dialCode, String phoneNumber, String clientIp) {
        PhoneNumberValidationService.ValidationResult validation = validateSupportedPhone(dialCode, phoneNumber);
        if (!validation.allowed()) {
            return sendFail("Phone number validation failed.", validation.reasonCode(), validation.normalizedE164());
        }
        if (StrUtil.isBlank(properties.getTemplateCode())) {
            return sendFail("SMS template is not configured.", REASON_TEMPLATE_MISSING, validation.normalizedE164());
        }

        String normalizedPhone = validation.normalizedE164();
        if (!markCooldown(normalizedPhone)) {
            return sendFail("Please wait before requesting another SMS code.", REASON_RATE_LIMITED, normalizedPhone);
        }
        if (!incrementWithinLimit(dailyKey(normalizedPhone), properties.getPhoneDailyLimit(), 1, TimeUnit.DAYS)) {
            return sendFail("SMS request limit reached for this phone number.", REASON_RATE_LIMITED, normalizedPhone);
        }
        if (StrUtil.isNotBlank(clientIp)
                && !incrementWithinLimit(ipKey(clientIp), properties.getIpHourlyLimit(), properties.getIpWindowMinutes(), TimeUnit.MINUTES)) {
            return sendFail("SMS request limit reached for this IP address.", REASON_RATE_LIMITED, normalizedPhone);
        }

        String code = RandomUtil.randomNumbers(6);
        String codeHash = hashCode(normalizedPhone, code);
        stringRedisTemplate.opsForValue().set(
                codeKey(normalizedPhone),
                codeHash,
                properties.getCodeTtlMinutes(),
                TimeUnit.MINUTES
        );

        try {
            smsCodeMessagePublisher.publishSmsCode(SmsCodeMessage.builder()
                    .messageId(IdUtil.nanoId(MESSAGE_ID_LENGTH))
                    .phoneNumber(normalizedPhone)
                    .templateCode(properties.getTemplateCode())
                    .code(code)
                    .expireMinutes(properties.getCodeTtlMinutes())
                    .scene(SCENE_BIND_PHONE)
                    .retryCount(0)
                    .createdAtEpochMilli(System.currentTimeMillis())
                    .build());
        } catch (RuntimeException e) {
            stringRedisTemplate.delete(codeKey(normalizedPhone));
            stringRedisTemplate.delete(cooldownKey(normalizedPhone));
            return sendFail("Failed to queue SMS code.", "SMS_QUEUE_FAILED", normalizedPhone);
        }

        return SmsCodeSendResult.builder()
                .success(true)
                .message("SMS code sent.")
                .reasonCode(REASON_OK)
                .normalizedE164(normalizedPhone)
                .build();
    }

    @Override
    public SmsCodeVerifyResult verifyBindPhoneCode(String dialCode, String phoneNumber, String code) {
        PhoneNumberValidationService.ValidationResult validation = validateSupportedPhone(dialCode, phoneNumber);
        if (!validation.allowed()) {
            return verifyFail("Phone number validation failed.", validation.reasonCode(), validation.normalizedE164());
        }
        String normalizedPhone = validation.normalizedE164();
        String normalizedCode = StrUtil.blankToDefault(code, "").trim();
        if (!normalizedCode.matches("\\d{6}")) {
            return verifyFail("SMS code is incorrect or expired.", REASON_CODE_INVALID, normalizedPhone);
        }

        String cachedHash = stringRedisTemplate.opsForValue().get(codeKey(normalizedPhone));
        if (StrUtil.isBlank(cachedHash) || !MessageDigest.isEqual(
                cachedHash.getBytes(StandardCharsets.UTF_8),
                hashCode(normalizedPhone, normalizedCode).getBytes(StandardCharsets.UTF_8))) {
            return verifyFail("SMS code is incorrect or expired.", REASON_CODE_INVALID, normalizedPhone);
        }

        stringRedisTemplate.delete(codeKey(normalizedPhone));
        stringRedisTemplate.delete(cooldownKey(normalizedPhone));
        return SmsCodeVerifyResult.builder()
                .success(true)
                .message("SMS code verified.")
                .reasonCode(REASON_OK)
                .normalizedE164(normalizedPhone)
                .build();
    }

    private PhoneNumberValidationService.ValidationResult validateSupportedPhone(String dialCode, String phoneNumber) {
        String normalizedDialCode = StrUtil.blankToDefault(dialCode, "").replace("+", "").trim();
        if (!properties.getSupportedDialCode().equals(normalizedDialCode)) {
            return PhoneNumberValidationService.ValidationResult.rejected(REASON_UNSUPPORTED_COUNTRY, null, null);
        }
        return phoneNumberValidationService.validateMobileLikeNumber(dialCode, phoneNumber);
    }

    private boolean markCooldown(String normalizedPhone) {
        Boolean marked = stringRedisTemplate.opsForValue().setIfAbsent(
                cooldownKey(normalizedPhone),
                "1",
                properties.getCooldownSeconds(),
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(marked);
    }

    private boolean incrementWithinLimit(String key, int limit, long ttl, TimeUnit timeUnit) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, ttl, timeUnit);
        }
        return count != null && count <= limit;
    }

    private String hashCode(String normalizedPhone, String code) {
        String input = normalizedPhone + ":" + code + ":" + properties.getCodeHashSecret();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", e);
        }
    }

    private String codeKey(String normalizedPhone) {
        return CODE_PREFIX + normalizedPhone;
    }

    private String cooldownKey(String normalizedPhone) {
        return COOLDOWN_PREFIX + normalizedPhone;
    }

    private String dailyKey(String normalizedPhone) {
        return DAILY_PREFIX + normalizedPhone;
    }

    private String ipKey(String clientIp) {
        return IP_PREFIX + clientIp;
    }

    private SmsCodeSendResult sendFail(String message, String reasonCode, String normalizedPhone) {
        return SmsCodeSendResult.builder()
                .success(false)
                .message(message)
                .reasonCode(reasonCode)
                .normalizedE164(normalizedPhone)
                .build();
    }

    private SmsCodeVerifyResult verifyFail(String message, String reasonCode, String normalizedPhone) {
        return SmsCodeVerifyResult.builder()
                .success(false)
                .message(message)
                .reasonCode(reasonCode)
                .normalizedE164(normalizedPhone)
                .build();
    }
}
