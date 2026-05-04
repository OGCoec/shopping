package com.example.ShoppingSystem.redisdata;

/**
 * Password reset Redis key constants.
 */
public final class PasswordResetRedisKeys {

    private PasswordResetRedisKeys() {
    }

    public static final String LINK_PREFIX = "auth:password-reset:link:";
    public static final String VERIFIED_PREFIX = "auth:password-reset:verified:";
    public static final String EMAIL_CODE_PREFIX = "auth:password-reset:code:";
    public static final String SEND_COOLDOWN_PREFIX = "auth:password-reset:send-cooldown:";
    public static final String WAF_VERIFIED_PREFIX = "auth:password-reset:waf-verified:";
    public static final String CRYPTO_KEY_PREFIX = "auth:password-reset:crypto:";
    public static final String CRYPTO_NONCE_PREFIX = "auth:password-reset:crypto:nonce:";

    public static final long LINK_TTL_MINUTES = 30L;
    public static final long VERIFIED_TTL_MINUTES = 5L;
    public static final long EMAIL_CODE_TTL_MINUTES = 5L;
    public static final long SEND_COOLDOWN_SECONDS = 60L;
    public static final long WAF_VERIFIED_TTL_MINUTES = 5L;
    public static final long CRYPTO_KEY_TTL_MINUTES = 10L;
}
