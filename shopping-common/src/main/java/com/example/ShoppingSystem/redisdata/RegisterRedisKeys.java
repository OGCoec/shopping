package com.example.ShoppingSystem.redisdata;

/**
 * Register flow Redis key constants.
 */
public final class RegisterRedisKeys {

    private RegisterRedisKeys() {
    }

    public static final String EMAIL_CODE_PREFIX = "auth:register:email-code:";
    public static final String EMAIL_CODE_META_PREFIX = "auth:register:email-code:meta:";
    public static final String EMAIL_CODE_CHALLENGE_PASSED_PREFIX = "auth:register:email-code:challenge-passed:";
    public static final String CAPTCHA_CODE_PREFIX = "auth:register:captcha:";
    public static final String CHALLENGE_PREFIX = "auth:register:challenge:";
    public static final String FLOW_PREFIX = "auth:register:flow:";

    /**
     * actual key: auth:register:challenge:{digest}:op-timeout:wait-until
     */
    public static final String CHALLENGE_OPERATION_TIMEOUT_WAIT_UNTIL_SUFFIX = ":op-timeout:wait-until";

    public static final long CAPTCHA_CODE_TTL_MINUTES = 5L;
    public static final long CHALLENGE_TTL_MINUTES = 5L;
    public static final long EMAIL_CODE_TTL_MINUTES = 5L;
    public static final long FLOW_TTL_MINUTES = 15L;
}
