package com.example.ShoppingSystem.service.user.auth.register;

import com.example.ShoppingSystem.redisdata.RegisterRedisKeys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Register flow session configuration.
 */
@Component
@ConfigurationProperties(prefix = "register.flow")
public class RegisterFlowProperties {

    private String redisKeyPrefix = RegisterRedisKeys.FLOW_PREFIX;
    private int ttlMinutes = (int) RegisterRedisKeys.FLOW_TTL_MINUTES;
    private String cookieName = "REGISTER_FLOW_ID";
    private String cookiePath = "/shopping/user";
    private boolean cookieHttpOnly = true;
    private boolean cookieSecure = false;
    private String cookieSameSite = "Lax";

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    public int getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(int ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public String getCookiePath() {
        return cookiePath;
    }

    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    public boolean isCookieHttpOnly() {
        return cookieHttpOnly;
    }

    public void setCookieHttpOnly(boolean cookieHttpOnly) {
        this.cookieHttpOnly = cookieHttpOnly;
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
}
