package com.example.ShoppingSystem.redisdata;

/**
 * Redis keys for user authentication risk windows.
 */
public final class UserAuthRiskRedisKeys {

    private UserAuthRiskRedisKeys() {
    }

    public static final long AUTH_FAILURE_WINDOW_MINUTES = 30L;
    public static final long NETWORK_RISK_WINDOW_MINUTES = 30L;
    public static final String AUTH_LOCK_SUFFIX = "auth:lock";

    private static final String USER_PREFIX = "risk:user:";
    private static final String WINDOW_30M_PREFIX = ":window:30m:auth:";
    private static final String NETWORK_WINDOW_30M_PREFIX = ":window:30m:network:";

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

    public static String networkScore30mKey(Long userId) {
        return networkWindow30mKey(userId, "score");
    }

    public static String networkWebRtcMismatch30mKey(Long userId) {
        return networkWindow30mKey(userId, "webrtc_mismatch");
    }

    public static String networkVpnProxy30mKey(Long userId) {
        return networkWindow30mKey(userId, "vpn_proxy");
    }

    public static String networkIpChanged30mKey(Long userId) {
        return networkWindow30mKey(userId, "ip_changed");
    }

    public static String networkCountryChanged30mKey(Long userId) {
        return networkWindow30mKey(userId, "country_changed");
    }

    public static String networkImpossibleTravel30mKey(Long userId) {
        return networkWindow30mKey(userId, "impossible_travel");
    }

    public static String networkWafRepeated30mKey(Long userId) {
        return networkWindow30mKey(userId, "waf_repeated");
    }

    public static String authLockKey(Long userId) {
        return USER_PREFIX + userId + ":" + AUTH_LOCK_SUFFIX;
    }

    private static String window30mKey(Long userId, String metric) {
        return USER_PREFIX + userId + WINDOW_30M_PREFIX + metric;
    }

    private static String networkWindow30mKey(Long userId, String metric) {
        return USER_PREFIX + userId + NETWORK_WINDOW_30M_PREFIX + metric;
    }
}
