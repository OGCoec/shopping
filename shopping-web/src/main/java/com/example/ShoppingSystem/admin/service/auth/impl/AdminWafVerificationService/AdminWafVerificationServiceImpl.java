package com.example.ShoppingSystem.admin.service.auth.impl.AdminWafVerificationService;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.admin.config.AdminSecurityProperties;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

import com.example.ShoppingSystem.admin.service.auth.AdminWafVerificationService;
@Service
public class AdminWafVerificationServiceImpl implements AdminWafVerificationService {

    public static final String ADMIN_WAF_REQUIRED_ERROR_CODE = "ADMIN_IP_CHANGED_WAF_REQUIRED";
    public static final String ADMIN_WAF_REQUIRED_MESSAGE = "检测到管理员访问 IP 变化，请完成安全验证后重试";

    private static final String VERIFIED_COOKIE_NAME = "ADMIN_WAF_VERIFIED";
    private static final String VERIFIED_KEY_PREFIX = "admin:waf:verified:";
    private static final Duration VERIFIED_TTL = Duration.ofSeconds(90);

    private final StringRedisTemplate stringRedisTemplate;
    private final AdminSecurityProperties properties;
    private final PreAuthRequestResolver requestResolver;

    public AdminWafVerificationServiceImpl(StringRedisTemplate stringRedisTemplate,
                                       AdminSecurityProperties properties,
                                       PreAuthRequestResolver requestResolver) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.requestResolver = requestResolver;
    }

    public ResponseCookie issueVerifiedCookie(HttpServletRequest request) {
        String ticket = IdUtil.nanoId(48);
        stringRedisTemplate.opsForValue().set(verifiedKey(ticket), "1", VERIFIED_TTL);
        return baseCookie(ticket, request)
                .maxAge(VERIFIED_TTL)
                .build();
    }

    public boolean consumeVerifiedTicket(HttpServletRequest request) {
        String ticket = resolveVerifiedTicket(request);
        if (StrUtil.isBlank(ticket)) {
            return false;
        }
        String consumed = stringRedisTemplate.opsForValue().getAndDelete(verifiedKey(ticket));
        return StrUtil.isNotBlank(consumed);
    }

    public ResponseCookie buildClearVerifiedCookie(HttpServletRequest request) {
        return baseCookie("", request)
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value, HttpServletRequest request) {
        return ResponseCookie.from(VERIFIED_COOKIE_NAME, StrUtil.blankToDefault(value, ""))
                .path(properties.getSessionCookiePath())
                .httpOnly(true)
                .secure(properties.isSessionCookieSecure() || requestResolver.isHttpsRequest(request))
                .sameSite(properties.getSessionCookieSameSite());
    }

    private String resolveVerifiedTicket(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return "";
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookie != null && VERIFIED_COOKIE_NAME.equals(cookie.getName())) {
                String value = StrUtil.blankToDefault(cookie.getValue(), "").trim();
                return value.length() == 48 ? value : "";
            }
        }
        return "";
    }

    private String verifiedKey(String ticket) {
        return VERIFIED_KEY_PREFIX + ticket;
    }
}
