package com.example.ShoppingSystem.registerflow;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import com.example.ShoppingSystem.service.user.auth.register.RegisterFlowProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Register flow cookie factory and resolver.
 */
@Component
public class RegisterFlowCookieFactory {

    private final RegisterFlowProperties properties;
    private final PreAuthRequestResolver preAuthRequestResolver;

    public RegisterFlowCookieFactory(RegisterFlowProperties properties,
                                     PreAuthRequestResolver preAuthRequestResolver) {
        this.properties = properties;
        this.preAuthRequestResolver = preAuthRequestResolver;
    }

    public ResponseCookie buildFlowCookie(String flowId, HttpServletRequest request) {
        return ResponseCookie.from(properties.getCookieName(), StrUtil.blankToDefault(flowId, ""))
                .httpOnly(properties.isCookieHttpOnly())
                .secure(properties.isCookieSecure() || preAuthRequestResolver.isHttpsRequest(request))
                .path(normalizeCookiePath(properties.getCookiePath()))
                .sameSite(normalizeSameSite(properties.getCookieSameSite()))
                .maxAge(Duration.ofMinutes(Math.max(1, properties.getTtlMinutes())))
                .build();
    }

    public ResponseCookie buildExpiredFlowCookie(HttpServletRequest request) {
        return ResponseCookie.from(properties.getCookieName(), "")
                .httpOnly(properties.isCookieHttpOnly())
                .secure(properties.isCookieSecure() || preAuthRequestResolver.isHttpsRequest(request))
                .path(normalizeCookiePath(properties.getCookiePath()))
                .sameSite(normalizeSameSite(properties.getCookieSameSite()))
                .maxAge(Duration.ZERO)
                .build();
    }

    public String resolveFlowId(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return "";
        }
        for (Cookie cookie : cookies) {
            if (cookie == null || !StrUtil.equals(cookie.getName(), properties.getCookieName())) {
                continue;
            }
            return StrUtil.blankToDefault(cookie.getValue(), "").trim();
        }
        return "";
    }

    private String normalizeCookiePath(String rawPath) {
        String normalized = StrUtil.blankToDefault(rawPath, "/").trim();
        if (normalized.isEmpty()) {
            return "/";
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String normalizeSameSite(String rawSameSite) {
        String normalized = StrUtil.blankToDefault(rawSameSite, "Lax").trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> "Lax";
        };
    }
}
