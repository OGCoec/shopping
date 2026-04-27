package com.example.ShoppingSystem.filter.preauth.support;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * preauth 模块的配置承载对象。
 * <p>
 * 所有 register.pre-auth.* 相关配置都会被绑定到这里，
 * 让业务代码通过一个对象读取，而不是到处散落 @Value。
 */
@Component
@ConfigurationProperties(prefix = "register.pre-auth")
public class PreAuthProperties {

    /** 是否启用预登录绑定机制。 */
    private boolean enabled = true;
    /** Redis 中保存完整绑定对象的 key 前缀。 */
    private String redisKeyPrefix = "register:preauth:bind:";
    /** 预登录绑定的基础 TTL，单位分钟。 */
    private int ttlMinutes = 60;
    /** recentIps 最多保留多少个 IP。 */
    private int recentIpLimit = 3;
    /** PREAUTH_TOKEN 的 Cookie 名。 */
    private String cookieName = "PREAUTH_TOKEN";
    /** PREAUTH_TOKEN Cookie 的路径。 */
    private String cookiePath = "/";
    /** PREAUTH_TOKEN 是否使用 HttpOnly。 */
    private boolean cookieHttpOnly = true;
    /** 是否强制使用 Secure Cookie。 */
    private boolean cookieSecure = false;
    /** Cookie 的 SameSite 策略。 */
    private String cookieSameSite = "Lax";
    /** 告知前端“当前需要先走 WAF”的 Cookie 名。 */
    private String wafRequiredCookieName = "WAF_REQUIRED";
    /** WAF_REQUIRED 提示 Cookie 的 TTL，单位分钟。 */
    private int wafRequiredCookieTtlMinutes = 10;

    /** 返回是否启用 preauth。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 设置是否启用 preauth。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 返回绑定对象的 Redis key 前缀。 */
    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    /** 设置绑定对象的 Redis key 前缀。 */
    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    /** 返回绑定 TTL 分钟数。 */
    public int getTtlMinutes() {
        return ttlMinutes;
    }

    /** 设置绑定 TTL 分钟数。 */
    public void setTtlMinutes(int ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    /** 返回 recentIps 的最大保留数量。 */
    public int getRecentIpLimit() {
        return recentIpLimit;
    }

    /** 设置 recentIps 的最大保留数量。 */
    public void setRecentIpLimit(int recentIpLimit) {
        this.recentIpLimit = recentIpLimit;
    }

    /** 返回 token Cookie 名。 */
    public String getCookieName() {
        return cookieName;
    }

    /** 设置 token Cookie 名。 */
    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    /** 返回 token Cookie 路径。 */
    public String getCookiePath() {
        return cookiePath;
    }

    /** 设置 token Cookie 路径。 */
    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    /** 返回 token Cookie 是否 HttpOnly。 */
    public boolean isCookieHttpOnly() {
        return cookieHttpOnly;
    }

    /** 设置 token Cookie 是否 HttpOnly。 */
    public void setCookieHttpOnly(boolean cookieHttpOnly) {
        this.cookieHttpOnly = cookieHttpOnly;
    }

    /** 返回 token Cookie 是否 Secure。 */
    public boolean isCookieSecure() {
        return cookieSecure;
    }

    /** 设置 token Cookie 是否 Secure。 */
    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    /** 返回 SameSite 策略。 */
    public String getCookieSameSite() {
        return cookieSameSite;
    }

    /** 设置 SameSite 策略。 */
    public void setCookieSameSite(String cookieSameSite) {
        this.cookieSameSite = cookieSameSite;
    }

    /** 返回 WAF_REQUIRED Cookie 名。 */
    public String getWafRequiredCookieName() {
        return wafRequiredCookieName;
    }

    /** 设置 WAF_REQUIRED Cookie 名。 */
    public void setWafRequiredCookieName(String wafRequiredCookieName) {
        this.wafRequiredCookieName = wafRequiredCookieName;
    }

    /** 返回 WAF_REQUIRED 提示 Cookie TTL 分钟数。 */
    public int getWafRequiredCookieTtlMinutes() {
        return wafRequiredCookieTtlMinutes;
    }

    /** 设置 WAF_REQUIRED 提示 Cookie TTL 分钟数。 */
    public void setWafRequiredCookieTtlMinutes(int wafRequiredCookieTtlMinutes) {
        this.wafRequiredCookieTtlMinutes = wafRequiredCookieTtlMinutes;
    }
}
