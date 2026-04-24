package com.example.ShoppingSystem.redisdata;

/**
 * Microsoft OAuth 相关 Redis 键常量。
 */
public final class MicrosoftRedisKeys {

    private MicrosoftRedisKeys() {
    }

    public static final String STATE_PREFIX = "auth:microsoft:state:";
    public static final long STATE_TTL_MINUTES = 5L;
}
