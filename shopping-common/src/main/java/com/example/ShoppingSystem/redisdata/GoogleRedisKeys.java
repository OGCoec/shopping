package com.example.ShoppingSystem.redisdata;

/**
 * Google OAuth 相关 Redis 键常量。
 */
public final class GoogleRedisKeys {

    private GoogleRedisKeys() {
    }

    public static final String STATE_PREFIX = "auth:google:state:";
    public static final long STATE_TTL_MINUTES = 5L;
}
