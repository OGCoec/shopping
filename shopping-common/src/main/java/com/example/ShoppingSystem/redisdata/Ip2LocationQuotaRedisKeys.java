package com.example.ShoppingSystem.redisdata;

/**
 * IP2Location.io 月额度 Redis 键规则。
 */
public final class Ip2LocationQuotaRedisKeys {

    private Ip2LocationQuotaRedisKeys() {
    }

    /**
     * Redis 第 3 分区内使用的键前缀。
     * 完整格式：ip2location:quota:{yyyy-MM-dd-HH:mm}:{apiKey}
     */
    public static final String QUOTA_PREFIX = "ip2location:quota:";

    /**
     * 当前所有额度键剩余额度总和。
     */
    public static final String QUOTA_COUNT_KEY = "ip2location:quota:count";

    /**
     * 轮询选择 quota key 的游标。
     */
    public static final String QUOTA_ROUND_ROBIN_CURSOR_KEY = "ip2location:round-robin:cursor";

    /**
     * 免费版每月默认额度。
     */
    public static final long MONTHLY_QUOTA = 50_000L;
}
