package com.example.ShoppingSystem.common.result;

/**
 * Enumerates the failure categories exposed by API responses.
 */
public enum FailureType {
    INVALID_EMAIL,
    USERNAME_REQUIRED,
    PASSWORD_TOO_SHORT,
    DEVICE_FINGERPRINT_REQUIRED,
    CAPTCHA_REQUIRED,
    CAPTCHA_INVALID,
    CHALLENGE_TYPE_MISMATCH,
    RISK_CHALLENGE_REQUIRED,
    EMAIL_CODE_SEND_FAILED,
    INTERNAL_ERROR
}
