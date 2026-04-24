package com.example.ShoppingSystem.redisdata;

/**
 * 注册流程 Redis 键常量。
 */
public final class RegisterRedisKeys {

    private RegisterRedisKeys() {
    }

    /**
     * 邮箱验证码键前缀。
     */
    public static final String EMAIL_CODE_PREFIX = "auth:register:email-code:";

    /**
     * 注册上下文元数据键前缀。
     */
    public static final String EMAIL_CODE_META_PREFIX = "auth:register:email-code:meta:";

    /**
     * 图形验证码键前缀。
     */
    public static final String CAPTCHA_CODE_PREFIX = "auth:register:captcha:";

    /**
     * 注册风险挑战状态键前缀。
     */
    public static final String CHALLENGE_PREFIX = "auth:register:challenge:";

    /**
     * OPERATION_TIMEOUT wait-until key suffix.
     * actual key: auth:register:challenge:{digest}:op-timeout:wait-until
     */
    public static final String CHALLENGE_OPERATION_TIMEOUT_WAIT_UNTIL_SUFFIX = ":op-timeout:wait-until";

    /**
     * 图形验证码有效期（分钟）。
     */
    public static final long CAPTCHA_CODE_TTL_MINUTES = 5L;

    /**
     * 注册风险挑战状态有效期（分钟）。
     */
    public static final long CHALLENGE_TTL_MINUTES = 5L;

    /**
     * 邮箱验证码有效期（分钟）。
     */
    public static final long EMAIL_CODE_TTL_MINUTES = 5L;
}
