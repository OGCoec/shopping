package com.example.ShoppingSystem.filter.preauth.support;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * 统一构建 preauth 相关 Cookie。
 * <p>
 * 这个类把 Cookie 的安全策略集中收口，包括：
 * HttpOnly、Secure、SameSite、Path 和 TTL。
 */
@Component
public class PreAuthCookieFactory {

    private final PreAuthProperties properties;
    private final PreAuthRequestResolver requestResolver;

    public PreAuthCookieFactory(PreAuthProperties properties,
                                PreAuthRequestResolver requestResolver) {
        this.properties = properties;
        this.requestResolver = requestResolver;
    }

    /**
     * 构建正常的 PREAUTH_TOKEN Cookie。
     */
    public ResponseCookie buildTokenCookie(String token, HttpServletRequest request) {
        return ResponseCookie.from(properties.getCookieName(), StrUtil.blankToDefault(token, ""))
                // token 默认走 HttpOnly，防止前端脚本直接读取。
                .httpOnly(properties.isCookieHttpOnly())
                // 如果配置要求或当前请求被识别为 HTTPS，则附带 Secure。
                .secure(properties.isCookieSecure() || requestResolver.isHttpsRequest(request))
                // 统一 path，保证站内页面都能带上这个 cookie。
                .path(normalizeCookiePath(properties.getCookiePath()))
                // 统一 SameSite，避免各处重复判断。
                .sameSite(normalizeSameSite(properties.getCookieSameSite()))
                // 使用绑定 TTL 作为 cookie 生命周期。
                .maxAge(bindingTtl())
                .build();
    }

    /**
     * 构建“清理 token”的过期 Cookie。
     */
    public ResponseCookie buildExpiredTokenCookie(HttpServletRequest request) {
        return ResponseCookie.from(properties.getCookieName(), "")
                .httpOnly(properties.isCookieHttpOnly())
                .secure(properties.isCookieSecure() || requestResolver.isHttpsRequest(request))
                .path(normalizeCookiePath(properties.getCookiePath()))
                .sameSite(normalizeSameSite(properties.getCookieSameSite()))
                // maxAge=0 表示让浏览器立刻删除该 cookie。
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * 构建“当前需要先走 WAF”的提示 Cookie。
     */
    public ResponseCookie buildWafRequiredCookie(HttpServletRequest request) {
        return ResponseCookie.from(properties.getWafRequiredCookieName(), "1")
                // 这个 cookie 需要被前端感知，因此不能走 HttpOnly。
                .httpOnly(false)
                .secure(properties.isCookieSecure() || requestResolver.isHttpsRequest(request))
                .path(normalizeCookiePath(properties.getCookiePath()))
                .sameSite(normalizeSameSite(properties.getCookieSameSite()))
                // WAF 提示 cookie 只用于前端感知当前处于挑战态，独立使用自己的 TTL 配置。
                .maxAge(Duration.ofMinutes(Math.max(1, properties.getWafRequiredCookieTtlMinutes())))
                .build();
    }

    /**
     * 构建“清理 WAF_REQUIRED”的过期 Cookie。
     */
    public ResponseCookie buildClearWafRequiredCookie(HttpServletRequest request) {
        return ResponseCookie.from(properties.getWafRequiredCookieName(), "")
                .httpOnly(false)
                .secure(properties.isCookieSecure() || requestResolver.isHttpsRequest(request))
                .path(normalizeCookiePath(properties.getCookiePath()))
                .sameSite(normalizeSameSite(properties.getCookieSameSite()))
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * 计算绑定类 Cookie 的统一 TTL。
     * <p>
     * 至少取 1 分钟，避免配置异常时出现 0 或负数。
     */
    private Duration bindingTtl() {
        return Duration.ofMinutes(Math.max(1, properties.getTtlMinutes()));
    }

    /**
     * 规范化 Cookie Path。
     */
    private String normalizeCookiePath(String rawPath) {
        String normalized = StrUtil.blankToDefault(rawPath, "/").trim();
        if (normalized.isEmpty()) {
            return "/";
        }

        // Cookie Path 必须以 "/" 开头；配置缺失时自动补齐。
        if (!normalized.startsWith("/")) {
            return "/" + normalized;
        }
        return normalized;
    }

    /**
     * 规范化 SameSite 字段。
     * <p>
     * 只接受浏览器标准支持的 Strict / Lax / None 三种形式。
     */
    private String normalizeSameSite(String rawSameSite) {
        String normalized = StrUtil.blankToDefault(rawSameSite, "Lax").trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> "Lax";
        };
    }
}
