package com.example.ShoppingSystem.redisdata;

/**
 * GitHub OAuth 相关 Redis 键常量。
 */
public final class GithubRedisKeys {

    private GithubRedisKeys() {
    }

    public static final String STATE_PREFIX = "auth:github:state:";
    public static final long STATE_TTL_MINUTES = 5L;
}
