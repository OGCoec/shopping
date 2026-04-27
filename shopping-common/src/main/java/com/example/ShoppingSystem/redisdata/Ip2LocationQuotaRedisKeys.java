package com.example.ShoppingSystem.redisdata;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * IP2Location.io quota key conventions stored in Redis.
 */
public final class Ip2LocationQuotaRedisKeys {

    private static final DateTimeFormatter QUOTA_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm");

    private Ip2LocationQuotaRedisKeys() {
    }

    /**
     * Key format: ip2location:quota:{accountType}:{yyyy-MM-dd-HH:mm}:{apiKey}
     */
    public static final String QUOTA_PREFIX = "ip2location:quota:";

    /**
     * Sum of all remaining quota values across active quota keys.
     */
    public static final String QUOTA_COUNT_KEY = "ip2location:quota:count";

    /**
     * Cursor used for round-robin selection among active quota keys.
     */
    public static final String QUOTA_ROUND_ROBIN_CURSOR_KEY = "ip2location:round-robin:cursor";

    /**
     * Trial lifecycle for FREE-account keys.
     */
    public static final Duration FREE_TRIAL_TTL = Duration.ofDays(7);

    /**
     * Rolling lifecycle for paid plans.
     */
    public static final Duration PAID_PLAN_TTL = Duration.ofDays(30);

    public static String buildQuotaKey(AccountType accountType, LocalDateTime dateTime, String apiKey) {
        AccountType safeAccountType = accountType == null ? AccountType.STARTER : accountType;
        return QUOTA_PREFIX
                + safeAccountType.name()
                + ":"
                + dateTime.format(QUOTA_TIME_FORMATTER)
                + ":"
                + apiKey;
    }

    public static AccountType extractAccountType(String quotaKey) {
        if (quotaKey == null || quotaKey.isBlank()) {
            return AccountType.STARTER;
        }
        String[] parts = quotaKey.split(":", 5);
        if (parts.length >= 5) {
            AccountType parsed = AccountType.parseOrNull(parts[2]);
            if (parsed != null) {
                return parsed;
            }
        }
        return AccountType.STARTER;
    }

    public static String extractApiKey(String quotaKey) {
        if (quotaKey == null || quotaKey.isBlank()) {
            return null;
        }
        int index = quotaKey.lastIndexOf(':');
        if (index < 0 || index == quotaKey.length() - 1) {
            return null;
        }
        return quotaKey.substring(index + 1).trim();
    }

    public enum AccountType {
        FREE(50_000L, FREE_TRIAL_TTL),
        STARTER(150_000L, PAID_PLAN_TTL),
        PLUS(300_000L, PAID_PLAN_TTL),
        SECURITY(600_000L, PAID_PLAN_TTL);

        private final long defaultMonthlyQuota;
        private final Duration lifecycleTtl;

        AccountType(long defaultMonthlyQuota, Duration lifecycleTtl) {
            this.defaultMonthlyQuota = defaultMonthlyQuota;
            this.lifecycleTtl = lifecycleTtl;
        }

        public long defaultMonthlyQuota() {
            return defaultMonthlyQuota;
        }

        public Duration lifecycleTtl() {
            return lifecycleTtl;
        }

        public static AccountType parse(String raw) {
            AccountType parsed = parseOrNull(raw);
            return parsed == null ? STARTER : parsed;
        }

        public static AccountType parseOrNull(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "FREE", "TRIAL" -> FREE;
                case "STARTER", "ADVANCED", "PAID" -> STARTER;
                case "PLUS" -> PLUS;
                case "SECURITY" -> SECURITY;
                default -> null;
            };
        }
    }
}
