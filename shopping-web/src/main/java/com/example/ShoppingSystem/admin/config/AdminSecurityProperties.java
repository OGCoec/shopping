package com.example.ShoppingSystem.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AdminSecurityProperties {

    private final String sessionCookieName;
    private final String sessionRedisKeyPrefix;
    private final long sessionTtlMinutes;
    private final String sessionCookiePath;
    private final boolean sessionCookieHttpOnly;
    private final boolean sessionCookieSecure;
    private final String sessionCookieSameSite;
    private final long emailCodeTtlMinutes;
    private final long emailCodeCooldownSeconds;
    private final boolean loginLockEnabled;
    private final int loginLockMaxFailures;
    private final long loginLockFailureWindowSeconds;
    private final long loginLockSeconds;
    private final String loginLockKeySecret;

    public AdminSecurityProperties(
            @Value("${admin.security.session-cookie-name:ADMIN_SESSION_TOKEN}") String sessionCookieName,
            @Value("${admin.security.session-redis-key-prefix:admin:session:}") String sessionRedisKeyPrefix,
            @Value("${admin.security.session-ttl-minutes:480}") long sessionTtlMinutes,
            @Value("${admin.security.session-cookie-path:/shopping/admin}") String sessionCookiePath,
            @Value("${admin.security.session-cookie-http-only:true}") boolean sessionCookieHttpOnly,
            @Value("${admin.security.session-cookie-secure:false}") boolean sessionCookieSecure,
            @Value("${admin.security.session-cookie-same-site:Lax}") String sessionCookieSameSite,
            @Value("${admin.security.email-code-ttl-minutes:5}") long emailCodeTtlMinutes,
            @Value("${admin.security.email-code-cooldown-seconds:60}") long emailCodeCooldownSeconds,
            @Value("${admin.security.login-lock.enabled:true}") boolean loginLockEnabled,
            @Value("${admin.security.login-lock.max-failures:5}") int loginLockMaxFailures,
            @Value("${admin.security.login-lock.failure-window-seconds:600}") long loginLockFailureWindowSeconds,
            @Value("${admin.security.login-lock.lock-seconds:1800}") long loginLockSeconds,
            @Value("${admin.security.login-lock.key-secret:${ADMIN_LOGIN_LOCK_KEY_SECRET:shopping-admin-login-lock-dev-secret}}") String loginLockKeySecret) {
        this.sessionCookieName = sessionCookieName;
        this.sessionRedisKeyPrefix = sessionRedisKeyPrefix;
        this.sessionTtlMinutes = sessionTtlMinutes;
        this.sessionCookiePath = sessionCookiePath;
        this.sessionCookieHttpOnly = sessionCookieHttpOnly;
        this.sessionCookieSecure = sessionCookieSecure;
        this.sessionCookieSameSite = sessionCookieSameSite;
        this.emailCodeTtlMinutes = emailCodeTtlMinutes;
        this.emailCodeCooldownSeconds = emailCodeCooldownSeconds;
        this.loginLockEnabled = loginLockEnabled;
        this.loginLockMaxFailures = loginLockMaxFailures;
        this.loginLockFailureWindowSeconds = loginLockFailureWindowSeconds;
        this.loginLockSeconds = loginLockSeconds;
        this.loginLockKeySecret = loginLockKeySecret;
    }

    public String getSessionCookieName() {
        return sessionCookieName;
    }

    public String getSessionRedisKeyPrefix() {
        return sessionRedisKeyPrefix;
    }

    public long getSessionTtlMinutes() {
        return sessionTtlMinutes;
    }

    public String getSessionCookiePath() {
        return sessionCookiePath;
    }

    public boolean isSessionCookieHttpOnly() {
        return sessionCookieHttpOnly;
    }

    public boolean isSessionCookieSecure() {
        return sessionCookieSecure;
    }

    public String getSessionCookieSameSite() {
        return sessionCookieSameSite;
    }

    public long getEmailCodeTtlMinutes() {
        return emailCodeTtlMinutes;
    }

    public long getEmailCodeCooldownSeconds() {
        return emailCodeCooldownSeconds;
    }

    public boolean isLoginLockEnabled() {
        return loginLockEnabled;
    }

    public int getLoginLockMaxFailures() {
        return loginLockMaxFailures;
    }

    public long getLoginLockFailureWindowSeconds() {
        return loginLockFailureWindowSeconds;
    }

    public long getLoginLockSeconds() {
        return loginLockSeconds;
    }

    public String getLoginLockKeySecret() {
        return loginLockKeySecret;
    }
}
