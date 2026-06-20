package com.example.ShoppingSystem.admin.service.auth.impl.AdminSessionService;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.admin.config.AdminSecurityProperties;
import com.example.ShoppingSystem.admin.dto.AdminSessionMeResponse;
import com.example.ShoppingSystem.admin.model.AdminAccount;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
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

import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
@Service
public class AdminSessionServiceImpl implements AdminSessionService {

    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_PHONE = "phone";
    private static final String FIELD_CURRENT_IP = "currentIp";
    private static final String FIELD_LAST_SEEN_AT = "lastSeenAt";
    private static final String FIELD_WEBRTC_IPS = "webRtcIps";
    private static final String FIELD_WEBRTC_STATUS = "webRtcStatus";
    private static final String FIELD_WEBRTC_SEEN_AT = "webRtcSeenAt";
    private static final String FIELD_WEBRTC_MISMATCH_COUNT = "webRtcMismatchCount";
    private static final String FIELD_WEBRTC_RISK_LEVEL = "webRtcRiskLevel";

    private final AdminSecurityProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final PreAuthRequestResolver requestResolver;

    public AdminSessionServiceImpl(AdminSecurityProperties properties,
                               StringRedisTemplate stringRedisTemplate,
                               PreAuthRequestResolver requestResolver) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.requestResolver = requestResolver;
    }

    public void authenticate(HttpServletRequest request,
                             HttpServletResponse response,
                             AdminAccount account) {
        String token = IdUtil.nanoId(48);
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, String> session = new LinkedHashMap<>();
        session.put(FIELD_USERNAME, safe(account.getUsername()));
        session.put(FIELD_EMAIL, safe(account.getEmail()));
        session.put(FIELD_PHONE, safe(account.getPhone()));
        session.put(FIELD_CURRENT_IP, resolveClientIp(request));
        session.put(FIELD_LAST_SEEN_AT, now.toString());

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

    public String resolveClientIp(HttpServletRequest request) {
        return requestResolver.resolveClientIp(request);
    }

    public boolean isCurrentIpAllowed(HttpServletRequest request, String currentIp) {
        String token = resolveSessionToken(request);
        if (StrUtil.isBlank(token)) {
            return false;
        }
        String key = sessionKey(token);
        Object stored = stringRedisTemplate.opsForHash().get(key, FIELD_CURRENT_IP);
        String storedIp = readString(stored);
        return StrUtil.isNotBlank(storedIp) && StrUtil.equals(storedIp, safe(currentIp));
    }

    public void refreshCurrentIp(HttpServletRequest request, String currentIp) {
        String token = resolveSessionToken(request);
        if (StrUtil.isBlank(token)) {
            return;
        }
        String key = sessionKey(token);
        stringRedisTemplate.opsForHash().put(key, FIELD_CURRENT_IP, safe(currentIp));
        stringRedisTemplate.expire(key, sessionTtl());
    }

    public void updateWebRtcRisk(String token,
                                 String webRtcIps,
                                 String webRtcStatus,
                                 long seenAtEpochMillis,
                                 int mismatchIncrement,
                                 String riskLevel) {
        if (StrUtil.isBlank(token)) {
            return;
        }
        String key = sessionKey(token.trim());
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (!Boolean.TRUE.equals(exists)) {
            return;
        }
        int currentMismatchCount = parseInt(stringRedisTemplate.opsForHash().get(key, FIELD_WEBRTC_MISMATCH_COUNT));
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put(FIELD_WEBRTC_IPS, safe(webRtcIps));
        updates.put(FIELD_WEBRTC_STATUS, safe(webRtcStatus));
        updates.put(FIELD_WEBRTC_SEEN_AT, String.valueOf(Math.max(0L, seenAtEpochMillis)));
        updates.put(FIELD_WEBRTC_MISMATCH_COUNT, String.valueOf(Math.max(0, currentMismatchCount + Math.max(0, mismatchIncrement))));
        updates.put(FIELD_WEBRTC_RISK_LEVEL, safe(riskLevel));
        stringRedisTemplate.opsForHash().putAll(key, updates);
        stringRedisTemplate.expire(key, sessionTtl());
    }

    private void touch(String key) {
        stringRedisTemplate.opsForHash().put(key, FIELD_LAST_SEEN_AT, OffsetDateTime.now().toString());
        stringRedisTemplate.expire(key, sessionTtl());
    }

    public String resolveSessionToken(HttpServletRequest request) {
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

    private int parseInt(Object value) {
        if (value == null || StrUtil.isBlank(value.toString())) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
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
