package com.example.ShoppingSystem.security.token;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.Utils.JwtUtils;
import com.example.ShoppingSystem.avatar.AvatarMetadataUtils;
import com.example.ShoppingSystem.common.transaction.AfterCommitExecutor;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.entity.entity.UserProfile;
import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.example.ShoppingSystem.mapper.user.UserLoginIdentityMapper;
import com.example.ShoppingSystem.mapper.user.UserProfileMapper;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceRiskProfileWriteService;
import com.example.ShoppingSystem.service.user.auth.risk.UserAuthFailureRiskService;
import com.example.ShoppingSystem.service.user.auth.risk.model.UserAuthLockStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.ResponseCookie;
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

public interface AuthTokenService {
    public void issueLoginTokens(Long userId,
                                 String preAuthToken,
                                 HttpServletRequest request,
                                 HttpServletResponse response);

    public void issueLoginTokens(Long userId,
                                 String preAuthToken,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 String scene);

    public AuthUserContext authenticateAccessToken(String accessToken, String riskLevel);

    public AuthUserContext authenticateAccessToken(String accessToken, String riskLevel, boolean allowCachedContextFastPath);

    public AuthTokenRefreshResult refresh(HttpServletRequest request, HttpServletResponse response);

    public void logoutCurrentDevice(HttpServletRequest request, HttpServletResponse response);

    public void logoutAllDevices(Long userId, HttpServletRequest request, HttpServletResponse response);

    public AuthUserContext loadOrRebuildUserContext(Long userId, String riskLevel);

    public void evictUserContext(Long userId);

    public void clearAuthCookies(HttpServletResponse response, HttpServletRequest request);

    public String resolveAccessToken(HttpServletRequest request);

    public Long tryResolveUserIdFromAccessToken(HttpServletRequest request);
}
