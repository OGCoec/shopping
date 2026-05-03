package com.example.ShoppingSystem.redisdata;

/**
 * Login flow Redis key constants.
 */
public final class LoginRedisKeys {

    private LoginRedisKeys() {
    }

    public static final String EMAIL_CODE_PREFIX = "auth:login:email-code:";
    public static final String CHALLENGE_PREFIX = "auth:login:challenge:";
    public static final String WAF_VERIFIED_PREFIX = "auth:login:waf-verified:";

    public static final long EMAIL_CODE_TTL_MINUTES = 5L;
    public static final long CHALLENGE_TTL_MINUTES = 5L;
    public static final long WAF_VERIFIED_TTL_MINUTES = 5L;

    public static final String CHALLENGE_OPERATION_TIMEOUT_WAIT_UNTIL_SUFFIX = ":op-timeout:wait-until";
}
