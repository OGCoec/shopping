package com.example.ShoppingSystem.redisdata;

/**
 * Redis key constants for automated login/register traffic defense.
 */
public final class BotDefenseRedisKeys {

    private BotDefenseRedisKeys() {
    }

    public static final String REGISTER_BLOCK_DEVICE_PREFIX = "register:block:device:";
    public static final String REGISTER_BLOCK_IP_PREFIX = "register:block:ip:";
    public static final String LOGIN_BLOCK_DEVICE_PREFIX = "login:block:device:";
    public static final String LOGIN_BLOCK_IP_PREFIX = "login:block:ip:";

    public static final String REGISTER_RATE_DEVICE_START_1S_PREFIX = "register:rate:device:start:1s:";
    public static final String REGISTER_RATE_DEVICE_START_1M_PREFIX = "register:rate:device:start:1m:";
    public static final String REGISTER_RATE_DEVICE_START_30M_PREFIX = "register:rate:device:start:30m:";
    public static final String REGISTER_RATE_IP_START_1S_PREFIX = "register:rate:ip:start:1s:";
    public static final String REGISTER_RATE_IP_START_1M_PREFIX = "register:rate:ip:start:1m:";
    public static final String REGISTER_RATE_IP_START_30M_PREFIX = "register:rate:ip:start:30m:";

    public static final String LOGIN_RATE_DEVICE_START_1S_PREFIX = "login:rate:device:start:1s:";
    public static final String LOGIN_RATE_DEVICE_START_1M_PREFIX = "login:rate:device:start:1m:";
    public static final String LOGIN_RATE_DEVICE_START_30M_PREFIX = "login:rate:device:start:30m:";
    public static final String LOGIN_RATE_IP_START_1S_PREFIX = "login:rate:ip:start:1s:";
    public static final String LOGIN_RATE_IP_START_1M_PREFIX = "login:rate:ip:start:1m:";
    public static final String LOGIN_RATE_IP_START_30M_PREFIX = "login:rate:ip:start:30m:";

    public static final String LOGIN_RATE_DEVICE_UNKNOWN_30M_PREFIX = "login:rate:device:unknown:30m:";
    public static final String LOGIN_RATE_IP_UNKNOWN_30M_PREFIX = "login:rate:ip:unknown:30m:";
}
