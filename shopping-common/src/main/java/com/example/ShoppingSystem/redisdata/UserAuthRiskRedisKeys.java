package com.example.ShoppingSystem.redisdata;

/**
 * Redis keys for user authentication risk windows.
 */
public final class UserAuthRiskRedisKeys {

    private UserAuthRiskRedisKeys() {
    }

    public static final long AUTH_FAILURE_WINDOW_MINUTES = 30L;
    public static final String AUTH_LOCK_SUFFIX = "auth:lock";

    private static final String USER_PREFIX = "risk:user:";
    private static final String WINDOW_30M_PREFIX = ":window:30m:auth:";

    public static String pwdFail30mKey(Long userId) {
        return window30mKey(userId, "pwd_fail");
    }

    public static String emailOtpFail30mKey(Long userId) {
        return window30mKey(userId, "email_otp_fail");
    }

    public static String smsOtpFail30mKey(Long userId) {
        return window30mKey(userId, "sms_otp_fail");
    }

    public static String failTotal30mKey(Long userId) {
        return window30mKey(userId, "fail_total");
    }

    public static String authLockKey(Long userId) {
        return USER_PREFIX + userId + ":" + AUTH_LOCK_SUFFIX;
    }

    private static String window30mKey(Long userId, String metric) {
        return USER_PREFIX + userId + WINDOW_30M_PREFIX + metric;
    }
}
