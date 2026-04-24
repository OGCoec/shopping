package com.example.ShoppingSystem.common.exception;

/**
 * Raised when the Tianai captcha payload itself is malformed and cannot be parsed
 * into a valid validation request.
 */
public class TianaiCaptchaFormatException extends RuntimeException {

    private final String captchaId;

    public TianaiCaptchaFormatException(String message) {
        this(message, null, null);
    }

    public TianaiCaptchaFormatException(String message, String captchaId) {
        this(message, captchaId, null);
    }

    public TianaiCaptchaFormatException(String message, String captchaId, Throwable cause) {
        super(message, cause);
        this.captchaId = captchaId;
    }

    public String getCaptchaId() {
        return captchaId;
    }
}
