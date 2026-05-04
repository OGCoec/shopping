package com.example.ShoppingSystem.service.user.auth.register.model;

/**
 * 注册挑战类型常量定义。
 * <p>
 * 统一集中定义 challengeType 与 challengeSubType，避免多处硬编码字符串。
 */
public final class RegisterChallengeConstants {

    private RegisterChallengeConstants() {
    }

    /**
     * Hutool 剪切验证码。
     */
    public static final String CHALLENGE_HUTOOL_SHEAR = "HUTOOL_SHEAR_CAPTCHA";

    /**
     * Tianai 图形验证码（具体玩法见 subType）。
     */
    public static final String CHALLENGE_TIANAI = "TIANAI_CAPTCHA";

    /**
     * Cloudflare Turnstile。
     */
    public static final String CHALLENGE_CLOUDFLARE_TURNSTILE = "CLOUDFLARE_TURNSTILE";

    /**
     * hCaptcha。
     */
    public static final String CHALLENGE_HCAPTCHA = "HCAPTCHA";

    /**
     * Google reCAPTCHA v2 checkbox.
     */
    public static final String CHALLENGE_GOOGLE_RECAPTCHA_V2 = "GOOGLE_RECAPTCHA_V2";

    /**
     * Legacy pending challenge value kept only for old Redis entries.
     */
    public static final String CHALLENGE_GOOGLE_RECAPTCHA_V3_LEGACY = "GOOGLE_RECAPTCHA_V3";

    /**
     * 超时阻断（无图形挑战，直接阻断）。
     */
    public static final String CHALLENGE_OPERATION_TIMEOUT = "OPERATION_TIMEOUT";

    /**
     * Tianai 子类型：滑块。
     */
    public static final String SUBTYPE_TIANAI_SLIDER = "SLIDER";

    /**
     * Tianai 子类型：旋转。
     */
    public static final String SUBTYPE_TIANAI_ROTATE = "ROTATE";

    /**
     * Tianai 子类型：拼接。
     */
    public static final String SUBTYPE_TIANAI_CONCAT = "CONCAT";

    /**
     * Tianai 子类型：汉字点选。
     */
    public static final String SUBTYPE_TIANAI_WORD_IMAGE_CLICK = "WORD_IMAGE_CLICK";
}
