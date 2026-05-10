package com.example.ShoppingSystem.admin.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.admin.config.AdminSecurityProperties;
import com.example.ShoppingSystem.admin.dto.AdminSessionMeResponse;
import com.example.ShoppingSystem.admin.model.AdminAccount;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AdminSessionService {

    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_PHONE = "phone";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_SEEN_AT = "lastSeenAt";
    private static final String FIELD_EXPIRES_AT = "expiresAt";

    private final AdminSecurityProperties properties;
    private final StringRedisTemplate stringRedisTemplate;

    public AdminSessionService(AdminSecurityProperties properties,
                               StringRedisTemplate stringRedisTemplate) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void authenticate(HttpServletRequest request,
                             HttpServletResponse response,
                             AdminAccount account) {
        String token = IdUtil.nanoId(48);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plus(sessionTtl());

        Map<String, String> session = new LinkedHashMap<>();
        session.put(FIELD_USERNAME, safe(account.getUsername()));
        session.put(FIELD_EMAIL, safe(account.getEmail()));
        session.put(FIELD_PHONE, safe(account.getPhone()));
        session.put(FIELD_CREATED_AT, now.toString());
        session.put(FIELD_LAST_SEEN_AT, now.toString());
        session.put(FIELD_EXPIRES_AT, expiresAt.toString());

        String key = sessionKey(token);
        stringRedisTemplate.opsForHash().putAll(key, session);
        stringRedisTemplate.expire(key, sessionTtl());
        addSessionCookie(response, token);
    }

    public boolean isAuthenticated(HttpServletRequest request) {
        String token = resolveSessionToken(request);
        if (StrUtil.isBlank(token)) {
            return false;
        }
        String key = sessionKey(token);
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (!Boolean.TRUE.equals(exists)) {
            return false;
        }
        touch(key);
        return true;
    }

    public AdminSessionMeResponse current(HttpServletRequest request) {
        String token = resolveSessionToken(request);
        if (StrUtil.isBlank(token)) {
            return new AdminSessionMeResponse(false, "", "", "");
        }
        Map<Object, Object> session = stringRedisTemplate.opsForHash().entries(sessionKey(token));
        if (session == null || session.isEmpty()) {
            return new AdminSessionMeResponse(false, "", "", "");
        }
        touch(sessionKey(token));
        return new AdminSessionMeResponse(
                true,
                readString(session.get(FIELD_USERNAME)),
                readString(session.get(FIELD_EMAIL)),
                readString(session.get(FIELD_PHONE))
        );
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = resolveSessionToken(request);
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(sessionKey(token));
        }
        clearSessionCookie(response);
    }

    private void touch(String key) {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put(FIELD_LAST_SEEN_AT, now.toString());
        updates.put(FIELD_EXPIRES_AT, now.plus(sessionTtl()).toString());
        stringRedisTemplate.opsForHash().putAll(key, updates);
        stringRedisTemplate.expire(key, sessionTtl());
    }

    private String resolveSessionToken(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return "";
        }
        String cookieName = properties.getSessionCookieName();
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                String token = StrUtil.blankToDefault(cookie.getValue(), "").trim();
                return token.length() == 48 ? token : "";
            }
        }
        return "";
    }

    private void addSessionCookie(HttpServletResponse response, String token) {
        if (response == null) {
            return;
        }
        response.addHeader("Set-Cookie", baseCookie(token)
                .maxAge(sessionTtl())
                .build()
                .toString());
    }

    private void clearSessionCookie(HttpServletResponse response) {
        if (response == null) {
            return;
        }
        response.addHeader("Set-Cookie", baseCookie("")
                .maxAge(Duration.ZERO)
                .build()
                .toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.getSessionCookieName(), StrUtil.blankToDefault(value, ""))
                .path(properties.getSessionCookiePath())
                .httpOnly(properties.isSessionCookieHttpOnly())
                .secure(properties.isSessionCookieSecure())
                .sameSite(properties.getSessionCookieSameSite());
    }

    private String sessionKey(String token) {
        return properties.getSessionRedisKeyPrefix() + token;
    }

    private Duration sessionTtl() {
        return Duration.ofMinutes(Math.max(1, properties.getSessionTtlMinutes()));
    }

    private String readString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
