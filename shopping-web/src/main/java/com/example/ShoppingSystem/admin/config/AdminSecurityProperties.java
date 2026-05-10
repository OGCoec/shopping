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

    public AdminSecurityProperties(
            @Value("${admin.security.session-cookie-name:ADMIN_SESSION_TOKEN}") String sessionCookieName,
            @Value("${admin.security.session-redis-key-prefix:admin:session:}") String sessionRedisKeyPrefix,
            @Value("${admin.security.session-ttl-minutes:480}") long sessionTtlMinutes,
            @Value("${admin.security.session-cookie-path:/shopping/admin}") String sessionCookiePath,
            @Value("${admin.security.session-cookie-http-only:true}") boolean sessionCookieHttpOnly,
            @Value("${admin.security.session-cookie-secure:false}") boolean sessionCookieSecure,
            @Value("${admin.security.session-cookie-same-site:Lax}") String sessionCookieSameSite,
            @Value("${admin.security.email-code-ttl-minutes:5}") long emailCodeTtlMinutes,
            @Value("${admin.security.email-code-cooldown-seconds:60}") long emailCodeCooldownSeconds) {
        this.sessionCookieName = sessionCookieName;
        this.sessionRedisKeyPrefix = sessionRedisKeyPrefix;
        this.sessionTtlMinutes = sessionTtlMinutes;
        this.sessionCookiePath = sessionCookiePath;
        this.sessionCookieHttpOnly = sessionCookieHttpOnly;
        this.sessionCookieSecure = sessionCookieSecure;
        this.sessionCookieSameSite = sessionCookieSameSite;
        this.emailCodeTtlMinutes = emailCodeTtlMinutes;
        this.emailCodeCooldownSeconds = emailCodeCooldownSeconds;
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
}
