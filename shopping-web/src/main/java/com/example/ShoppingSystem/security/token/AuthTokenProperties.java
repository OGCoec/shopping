package com.example.ShoppingSystem.security.token;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "shopping.auth-token")
public class AuthTokenProperties {

    private String accessCookieName = "ACCESS_TOKEN";
    private String refreshCookieName = "REFRESH_TOKEN";
    private String accessCookiePath = "/";
    private String refreshCookiePath = "/";
    private boolean cookieSecure = false;
    private String cookieSameSite = "Lax";
    private long accessTtlSeconds = 10 * 60;
    private long accessCookieTtlSeconds = 3 * 60 * 60;
    private long refreshTtlSeconds = 3 * 60 * 60;
    private long userContextTtlSeconds = 3 * 60 * 60 + 5 * 60;
    private String refreshRedisKeyPrefix = "auth:user:refresh:";
    private String userContextRedisKeyPrefix = "auth:user:context:";

    public String getAccessCookieName() {
        return accessCookieName;
    }

    public void setAccessCookieName(String accessCookieName) {
        this.accessCookieName = accessCookieName;
    }

    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    public void setRefreshCookieName(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }

    public String getAccessCookiePath() {
        return accessCookiePath;
    }

    public void setAccessCookiePath(String accessCookiePath) {
        this.accessCookiePath = accessCookiePath;
    }

    public String getRefreshCookiePath() {
        return refreshCookiePath;
    }

    public void setRefreshCookiePath(String refreshCookiePath) {
        this.refreshCookiePath = refreshCookiePath;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String getCookieSameSite() {
        return cookieSameSite;
    }

    public void setCookieSameSite(String cookieSameSite) {
        this.cookieSameSite = cookieSameSite;
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public void setAccessTtlSeconds(long accessTtlSeconds) {
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public long getAccessCookieTtlSeconds() {
        return accessCookieTtlSeconds;
    }

    public void setAccessCookieTtlSeconds(long accessCookieTtlSeconds) {
        this.accessCookieTtlSeconds = accessCookieTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    public void setRefreshTtlSeconds(long refreshTtlSeconds) {
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public long getUserContextTtlSeconds() {
        return userContextTtlSeconds;
    }

    public void setUserContextTtlSeconds(long userContextTtlSeconds) {
        this.userContextTtlSeconds = userContextTtlSeconds;
    }

    public String getRefreshRedisKeyPrefix() {
        return refreshRedisKeyPrefix;
    }

    public void setRefreshRedisKeyPrefix(String refreshRedisKeyPrefix) {
        this.refreshRedisKeyPrefix = refreshRedisKeyPrefix;
    }

    public String getUserContextRedisKeyPrefix() {
        return userContextRedisKeyPrefix;
    }

    public void setUserContextRedisKeyPrefix(String userContextRedisKeyPrefix) {
        this.userContextRedisKeyPrefix = userContextRedisKeyPrefix;
    }
}
