package com.example.ShoppingSystem.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PhoneNumberValidationService {

    public static final String REASON_OK = "OK";
    public static final String REASON_INVALID_PHONE = "PHONE_INVALID";
    public static final String REASON_INVALID_DIAL_CODE = "PHONE_INVALID_DIAL_CODE";
    public static final String REASON_VOIP_NOT_ALLOWED = "PHONE_VOIP_NOT_ALLOWED";
    public static final String REASON_FIXED_LINE_NOT_ALLOWED = "PHONE_FIXED_LINE_NOT_ALLOWED";
    public static final String REASON_TYPE_NOT_ALLOWED = "PHONE_TYPE_NOT_ALLOWED";

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    public ValidationResult validateMobileLikeNumber(String dialCode, String rawPhoneNumber) {
        String normalizedDialCode = normalizeDialCode(dialCode);
        String normalizedRawPhone = rawPhoneNumber == null
                ? ""
                : PhoneNumberUtil.normalizeDigitsOnly(rawPhoneNumber);

        if (normalizedDialCode == null) {
            return ValidationResult.rejected(REASON_INVALID_DIAL_CODE, null, null);
        }
        if (normalizedRawPhone.isBlank()) {
            return ValidationResult.rejected(REASON_INVALID_PHONE, null, null);
        }

        int countryCode;
        try {
            countryCode = Integer.parseInt(normalizedDialCode);
        } catch (NumberFormatException ignored) {
            return ValidationResult.rejected(REASON_INVALID_DIAL_CODE, null, null);
        }

        PhoneNumber parsedNumber = parseByRegionCandidates(countryCode, normalizedRawPhone);
        if (parsedNumber == null) {
            parsedNumber = parseAsInternationalNumber(normalizedDialCode, normalizedRawPhone);
        }
        if (parsedNumber == null || !phoneNumberUtil.isValidNumber(parsedNumber)) {
            return ValidationResult.rejected(REASON_INVALID_PHONE, null, null);
        }

        PhoneNumberType numberType = phoneNumberUtil.getNumberType(parsedNumber);
        String normalizedE164 = phoneNumberUtil.format(parsedNumber, PhoneNumberFormat.E164);

        return switch (numberType) {
            case MOBILE, FIXED_LINE_OR_MOBILE ->
                    ValidationResult.accepted(REASON_OK, numberType.name(), normalizedE164);
            case VOIP ->
                    ValidationResult.rejected(REASON_VOIP_NOT_ALLOWED, numberType.name(), normalizedE164);
            case FIXED_LINE ->
                    ValidationResult.rejected(REASON_FIXED_LINE_NOT_ALLOWED, numberType.name(), normalizedE164);
            default ->
                    ValidationResult.rejected(REASON_TYPE_NOT_ALLOWED, numberType.name(), normalizedE164);
        };
    }

    private PhoneNumber parseByRegionCandidates(int countryCode, String rawPhoneNumber) {
        List<String> regionCodes = phoneNumberUtil.getRegionCodesForCountryCode(countryCode);
        for (String regionCode : regionCodes) {
            PhoneNumber parsedNumber = parsePhoneNumber(rawPhoneNumber, regionCode);
            if (parsedNumber == null) {
                continue;
            }
            if (parsedNumber.getCountryCode() != countryCode) {
                continue;
            }
            if (phoneNumberUtil.isValidNumberForRegion(parsedNumber, regionCode)) {
                return parsedNumber;
            }
        }
        return null;
    }

    private PhoneNumber parseAsInternationalNumber(String normalizedDialCode, String normalizedRawPhone) {
        return parsePhoneNumber("+" + normalizedDialCode + normalizedRawPhone, "ZZ");
    }

    private PhoneNumber parsePhoneNumber(String candidate, String regionCode) {
        try {
            return phoneNumberUtil.parse(candidate, regionCode);
        } catch (NumberParseException ignored) {
            return null;
        }
    }

    private String normalizeDialCode(String dialCode) {
        if (dialCode == null || dialCode.isBlank()) {
            return null;
        }
        String digitsOnly = PhoneNumberUtil.normalizeDigitsOnly(dialCode);
        return digitsOnly.isBlank() ? null : digitsOnly;
    }

    public record ValidationResult(boolean allowed,
                                   String reasonCode,
                                   String phoneType,
                                   String normalizedE164) {

        public static ValidationResult accepted(String reasonCode,
                                                String phoneType,
                                                String normalizedE164) {
            return new ValidationResult(true, reasonCode, phoneType, normalizedE164);
        }

        public static ValidationResult rejected(String reasonCode,
                                                String phoneType,
                                                String normalizedE164) {
            return new ValidationResult(false, reasonCode, phoneType, normalizedE164);
        }
    }
}
