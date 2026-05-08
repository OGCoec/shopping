package com.example.ShoppingSystem.security.token;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.Utils.JwtUtils;
import com.example.ShoppingSystem.avatar.AvatarMetadataUtils;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.entity.entity.UserProfile;
import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.mapper.UserProfileMapper;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceRiskProfileWriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Service
public class AuthTokenService {

    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_TOKEN_VERSION = "tokenVersion";
    private static final String CLAIM_JTI = "jti";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String LEGACY_REFRESH_COOKIE_PATH = "/shopping/user/auth";
    private static final String SCENE_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    private static final DefaultRedisScript<Long> DELETE_REFRESH_SESSIONS_SCRIPT = new DefaultRedisScript<>(
            """
                    local prefix = ARGV[1]
                    local cursor = "0"
                    local deleted = 0

                    repeat
                        local result = redis.call('SCAN', cursor, 'MATCH', prefix .. '*', 'COUNT', 100)
                        cursor = result[1]
                        local keys = result[2]
                        if #keys > 0 then
                            deleted = deleted + redis.call('DEL', unpack(keys))
                        end
                    until cursor == "0"

                    return deleted
                    """,
            Long.class
    );

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final UserProfileMapper userProfileMapper;
    private final AuthTokenProperties properties;
    private final PreAuthProperties preAuthProperties;
    private final DeviceRiskProfileWriteService deviceRiskProfileWriteService;

    public AuthTokenService(JwtUtils jwtUtils,
                            StringRedisTemplate stringRedisTemplate,
                            ObjectMapper objectMapper,
                            UserLoginIdentityMapper userLoginIdentityMapper,
                            UserProfileMapper userProfileMapper,
                            AuthTokenProperties properties,
                            PreAuthProperties preAuthProperties,
                            DeviceRiskProfileWriteService deviceRiskProfileWriteService) {
        this.jwtUtils = jwtUtils;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.userProfileMapper = userProfileMapper;
        this.properties = properties;
        this.preAuthProperties = preAuthProperties;
        this.deviceRiskProfileWriteService = deviceRiskProfileWriteService;
    }

    public void issueLoginTokens(Long userId,
                                 String preAuthToken,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        issueLoginTokens(userId, preAuthToken, request, response, SCENE_LOGIN_SUCCESS);
    }

    public void issueLoginTokens(Long userId,
                                 String preAuthToken,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 String scene) {
        UserLoginIdentity identity = requireActiveIdentity(userId);
        String riskLevel = resolveRiskLevel(request);
        AuthUserContext context = buildUserContext(identity, riskLevel);
        saveUserContext(context);

        String accessToken = generateAccessToken(context);
        String refreshToken = IdUtil.nanoId(48);
        long now = System.currentTimeMillis();
        RefreshSession refreshSession = new RefreshSession(
                userId,
                context.tokenVersion(),
                StrUtil.blankToDefault(preAuthToken, ""),
                hash(request == null ? "" : request.getHeader(PreAuthHeaders.HEADER_DEVICE_FINGERPRINT)),
                hash(resolveUserAgent(request)),
                resolveClientIp(request),
                riskLevel,
                now,
                now
        );
        saveRefreshSession(userId, refreshToken, refreshSession);
        addLoginCookies(response, request, accessToken, refreshToken);
        deviceRiskProfileWriteService.recordSuccess(
                userId,
                request == null ? "" : request.getHeader(PreAuthHeaders.HEADER_DEVICE_FINGERPRINT),
                resolveClientIp(request),
                StrUtil.blankToDefault(scene, SCENE_LOGIN_SUCCESS)
        );
    }

    public AuthUserContext authenticateAccessToken(String accessToken, String riskLevel) {
        Claims claims = jwtUtils.parseToken(accessToken).join();
        if (!ACCESS_TOKEN_TYPE.equals(String.valueOf(claims.get(CLAIM_TYPE)))) {
            throw new AuthTokenException(HttpServletResponse.SC_UNAUTHORIZED,
                    "ACCESS_TOKEN_INVALID",
                    "Access token type is invalid.");
        }
        Long userId = parseUserId(claims.getSubject());
        String tokenVersion = stringClaim(claims, CLAIM_TOKEN_VERSION);
        if (userId == null || StrUtil.isBlank(tokenVersion)) {
            throw new AuthTokenException(HttpServletResponse.SC_UNAUTHORIZED,
                    "ACCESS_TOKEN_INVALID",
                    "Access token payload is invalid.");
        }
        AuthUserContext context = loadOrRebuildUserContext(userId, riskLevel);
        if (!ACTIVE_STATUS.equals(context.status())) {
            throw new AuthTokenException(HttpServletResponse.SC_FORBIDDEN,
                    "USER_STATUS_ERROR",
                    "User is not active.");
        }
        if (!tokenVersion.equals(context.tokenVersion())) {
            throw new AuthTokenException(HttpServletResponse.SC_UNAUTHORIZED,
                    "TOKEN_VERSION_MISMATCH",
                    "Login session has been invalidated.");
        }
        return context;
    }

    public AuthTokenRefreshResult refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readCookie(request, properties.getRefreshCookieName());
        if (StrUtil.isBlank(refreshToken)) {
            clearAuthCookies(response, request);
            return AuthTokenRefreshResult.failed(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "REFRESH_TOKEN_MISSING",
                    "Refresh token is missing.");
        }
        String currentAccessToken = resolveAccessToken(request);
        if (StrUtil.isBlank(currentAccessToken)) {
            clearAuthCookies(response, request);
            return AuthTokenRefreshResult.failed(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "ACCESS_TOKEN_MISSING",
                    "Access token is missing.");
        }
        Long userId = resolveUserIdFromAccessTokenAllowExpired(currentAccessToken);
        if (userId == null) {
            clearAuthCookies(response, request);
            return AuthTokenRefreshResult.failed(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "ACCESS_TOKEN_INVALID",
                    "Access token is invalid.");
        }

        String refreshKey = refreshKey(userId, refreshToken);
        RefreshSession session = loadRefreshSession(refreshKey);
        if (session == null) {
            clearAuthCookies(response, request);
            return AuthTokenRefreshResult.failed(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "REFRESH_TOKEN_EXPIRED",
                    "Refresh token has expired.");
        }

        UserLoginIdentity identity = requireActiveIdentity(session.userId());
        if (!StrUtil.equals(session.tokenVersion(), identity.getTokenVersion())) {
            stringRedisTemplate.delete(refreshKey);
            clearAuthCookies(response, request);
            return AuthTokenRefreshResult.failed(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "TOKEN_VERSION_MISMATCH",
                    "Login session has been invalidated.");
        }

        AuthUserContext context = loadOrRebuildUserContext(session.userId(), session.riskLevel());
        String accessToken = generateAccessToken(context);
        long now = System.currentTimeMillis();
        RefreshSession refreshed = new RefreshSession(
                session.userId(),
                session.tokenVersion(),
                session.preAuthToken(),
                session.deviceFingerprintHash(),
                session.userAgentHash(),
                resolveClientIp(request),
                session.riskLevel(),
                session.createdAtEpochMillis(),
                now
        );
        saveRefreshSession(session.userId(), refreshToken, refreshed);
        touchPreAuthBinding(session.preAuthToken());
        touchUserContext(session.userId());
        addLoginCookies(response, request, accessToken, refreshToken);
        return AuthTokenRefreshResult.ok();
    }

    public void logoutCurrentDevice(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readCookie(request, properties.getRefreshCookieName());
        Long userId = resolveUserIdFromAccessTokenAllowExpired(resolveAccessToken(request));
        if (StrUtil.isNotBlank(refreshToken) && userId != null) {
            stringRedisTemplate.delete(refreshKey(userId, refreshToken));
        }
        clearAuthCookies(response, request);
    }

    public void logoutAllDevices(Long userId, HttpServletRequest request, HttpServletResponse response) {
        if (userId == null) {
            clearAuthCookies(response, request);
            return;
        }
        String nextTokenVersion = IdUtil.fastSimpleUUID().substring(0, 24);
        userLoginIdentityMapper.updateTokenVersionByUserId(userId, nextTokenVersion);
        deleteRefreshSessionsByUserId(userId);
        stringRedisTemplate.delete(userContextKey(userId));
        clearAuthCookies(response, request);
    }

    public AuthUserContext loadOrRebuildUserContext(Long userId, String riskLevel) {
        String key = userContextKey(userId);
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(cached)) {
            try {
                return objectMapper.readValue(cached, AuthUserContext.class);
            } catch (Exception ignored) {
                stringRedisTemplate.delete(key);
            }
        }
        UserLoginIdentity identity = requireActiveIdentity(userId);
        AuthUserContext context = buildUserContext(identity, riskLevel);
        saveUserContext(context);
        return context;
    }

    public void evictUserContext(Long userId) {
        if (userId == null) {
            return;
        }
        stringRedisTemplate.delete(userContextKey(userId));
    }

    private UserLoginIdentity requireActiveIdentity(Long userId) {
        if (userId == null) {
            throw new AuthTokenException(HttpServletResponse.SC_UNAUTHORIZED,
                    "USER_NOT_FOUND",
                    "User does not exist.");
        }
        UserLoginIdentity identity = userLoginIdentityMapper.findByUserId(userId);
        if (identity == null) {
            throw new AuthTokenException(HttpServletResponse.SC_UNAUTHORIZED,
                    "USER_NOT_FOUND",
                    "User does not exist.");
        }
        if (!ACTIVE_STATUS.equals(identity.getStatus())) {
            throw new AuthTokenException(HttpServletResponse.SC_FORBIDDEN,
                    "USER_STATUS_ERROR",
                    "User is not active.");
        }
        return identity;
    }

    private AuthUserContext buildUserContext(UserLoginIdentity identity, String riskLevel) {
        UserProfile profile = userProfileMapper.findById(identity.getUserId());
        String firstName = profile == null ? "" : StrUtil.blankToDefault(profile.getFirstName(), "");
        String lastName = profile == null ? "" : StrUtil.blankToDefault(profile.getLastName(), "");
        String email = StrUtil.blankToDefault(identity.getEmail(), "");
        String phone = StrUtil.blankToDefault(identity.getPhone(), "");
        String username = profile == null ? "" : StrUtil.blankToDefault(profile.getUsername(), "");
        String avatarUrl = profile == null ? "" : AvatarMetadataUtils.extractUrl(profile.getAvatar(), objectMapper);
        if (StrUtil.isBlank(username)) {
            username = StrUtil.blankToDefault(email, phone);
        }
        return new AuthUserContext(
                identity.getUserId(),
                username,
                firstName,
                lastName,
                StrUtil.blankToDefault(email, phone),
                email,
                phone,
                StrUtil.blankToDefault(identity.getStatus(), ACTIVE_STATUS),
                profile == null ? "" : StrUtil.blankToDefault(profile.getGender(), ""),
                avatarUrl,
                StrUtil.blankToDefault(identity.getTokenVersion(), ""),
                StrUtil.blankToDefault(riskLevel, "L1"),
                Set.of("USER")
        );
    }

    private void saveUserContext(AuthUserContext context) {
        try {
            stringRedisTemplate.opsForValue().set(
                    userContextKey(context.userId()),
                    objectMapper.writeValueAsString(context),
                    Math.max(1, properties.getUserContextTtlSeconds()),
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save user context", e);
        }
    }

    private void touchUserContext(Long userId) {
        stringRedisTemplate.expire(
                userContextKey(userId),
                Math.max(1, properties.getUserContextTtlSeconds()),
                TimeUnit.SECONDS
        );
    }

    private String generateAccessToken(AuthUserContext context) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", String.valueOf(context.userId()));
        claims.put(CLAIM_TYPE, ACCESS_TOKEN_TYPE);
        claims.put(CLAIM_JTI, IdUtil.nanoId(24));
        claims.put(CLAIM_TOKEN_VERSION, context.tokenVersion());
        return jwtUtils.generateToken(claims, Math.max(1, properties.getAccessTtlSeconds())).join();
    }

    private void saveRefreshSession(Long userId, String refreshToken, RefreshSession session) {
        try {
            stringRedisTemplate.opsForValue().set(
                    refreshKey(userId, refreshToken),
                    objectMapper.writeValueAsString(session),
                    Math.max(1, properties.getRefreshTtlSeconds()),
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save refresh session", e);
        }
    }

    private RefreshSession loadRefreshSession(String key) {
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, RefreshSession.class);
        } catch (Exception ignored) {
            stringRedisTemplate.delete(key);
            return null;
        }
    }

    private void touchPreAuthBinding(String preAuthToken) {
        if (StrUtil.isBlank(preAuthToken)) {
            return;
        }
        stringRedisTemplate.expire(
                preAuthProperties.getRedisKeyPrefix() + preAuthToken.trim(),
                Math.max(1, properties.getRefreshTtlSeconds()),
                TimeUnit.SECONDS
        );
    }

    private void deleteRefreshSessionsByUserId(Long userId) {
        String prefix = properties.getRefreshRedisKeyPrefix() + userId + ":";
        try {
            stringRedisTemplate.execute(DELETE_REFRESH_SESSIONS_SCRIPT, Collections.emptyList(), prefix);
        } catch (Exception ignored) {
        }
    }

    private void addLoginCookies(HttpServletResponse response,
                                 HttpServletRequest request,
                                 String accessToken,
                                 String refreshToken) {
        response.addHeader("Set-Cookie", buildCookie(
                properties.getAccessCookieName(),
                accessToken,
                properties.getAccessCookiePath(),
                Duration.ofSeconds(Math.max(1, properties.getAccessCookieTtlSeconds())),
                true,
                request
        ).toString());
        clearLegacyRefreshCookie(response, request);
        response.addHeader("Set-Cookie", buildCookie(
                properties.getRefreshCookieName(),
                refreshToken,
                properties.getRefreshCookiePath(),
                Duration.ofSeconds(Math.max(1, properties.getRefreshTtlSeconds())),
                true,
                request
        ).toString());
    }

    public void clearAuthCookies(HttpServletResponse response, HttpServletRequest request) {
        response.addHeader("Set-Cookie", buildCookie(
                properties.getAccessCookieName(),
                "",
                properties.getAccessCookiePath(),
                Duration.ZERO,
                true,
                request
        ).toString());
        response.addHeader("Set-Cookie", buildCookie(
                properties.getRefreshCookieName(),
                "",
                properties.getRefreshCookiePath(),
                Duration.ZERO,
                true,
                request
        ).toString());
        clearLegacyRefreshCookie(response, request);
    }

    private void clearLegacyRefreshCookie(HttpServletResponse response, HttpServletRequest request) {
        if (LEGACY_REFRESH_COOKIE_PATH.equals(normalizePath(properties.getRefreshCookiePath()))) {
            return;
        }
        response.addHeader("Set-Cookie", buildCookie(
                properties.getRefreshCookieName(),
                "",
                LEGACY_REFRESH_COOKIE_PATH,
                Duration.ZERO,
                true,
                request
        ).toString());
    }

    private ResponseCookie buildCookie(String name,
                                       String value,
                                       String path,
                                       Duration maxAge,
                                       boolean httpOnly,
                                       HttpServletRequest request) {
        return ResponseCookie.from(name, StrUtil.blankToDefault(value, ""))
                .httpOnly(httpOnly)
                .secure(properties.isCookieSecure() || isHttpsRequest(request))
                .path(normalizePath(path))
                .sameSite(normalizeSameSite(properties.getCookieSameSite()))
                .maxAge(maxAge)
                .build();
    }

    private String refreshKey(Long userId, String refreshToken) {
        return properties.getRefreshRedisKeyPrefix() + userId + ":" + refreshToken;
    }

    private String userContextKey(Long userId) {
        return properties.getUserContextRedisKeyPrefix() + userId;
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request == null || request.getCookies() == null || StrUtil.isBlank(name)) {
            return "";
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return StrUtil.blankToDefault(cookie.getValue(), "").trim();
            }
        }
        return "";
    }

    public String resolveAccessToken(HttpServletRequest request) {
        String token = readCookie(request, properties.getAccessCookieName());
        if (StrUtil.isNotBlank(token)) {
            return token;
        }
        String authorization = request == null ? "" : StrUtil.blankToDefault(request.getHeader("Authorization"), "");
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return "";
    }

    public Long tryResolveUserIdFromAccessToken(HttpServletRequest request) {
        return resolveUserIdFromAccessTokenAllowExpired(resolveAccessToken(request));
    }

    private Long resolveUserIdFromAccessTokenAllowExpired(String accessToken) {
        if (StrUtil.isBlank(accessToken)) {
            return null;
        }
        try {
            Claims claims = jwtUtils.parseToken(accessToken).join();
            return parseUserId(claims.getSubject());
        } catch (Exception e) {
            Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ExpiredJwtException expiredJwtException) {
                return parseUserId(expiredJwtException.getClaims().getSubject());
            }
            return null;
        }
    }

    private Long parseUserId(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stringClaim(Claims claims, String name) {
        Object value = claims == null ? null : claims.get(name);
        return value == null ? "" : String.valueOf(value);
    }

    private String resolveRiskLevel(HttpServletRequest request) {
        Object riskLevel = request == null ? null : request.getAttribute("preAuthRiskLevel");
        return riskLevel instanceof String text && StrUtil.isNotBlank(text) ? text.trim() : "L1";
    }

    private String resolveUserAgent(HttpServletRequest request) {
        return request == null ? "" : StrUtil.blankToDefault(request.getHeader("User-Agent"), "");
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(realIp)) {
            return realIp.trim();
        }
        return StrUtil.blankToDefault(request.getRemoteAddr(), "");
    }

    private boolean isHttpsRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return request.isSecure() || "https".equalsIgnoreCase(forwardedProto);
    }

    private String normalizePath(String path) {
        String normalized = StrUtil.blankToDefault(path, "/").trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String normalizeSameSite(String value) {
        String normalized = StrUtil.blankToDefault(value, "Lax").trim();
        if ("Strict".equalsIgnoreCase(normalized)) {
            return "Strict";
        }
        if ("None".equalsIgnoreCase(normalized)) {
            return "None";
        }
        return "Lax";
    }

    private String hash(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception ignored) {
            return "";
        }
    }
}
