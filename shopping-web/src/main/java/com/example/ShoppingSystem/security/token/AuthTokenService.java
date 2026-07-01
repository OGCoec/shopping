package com.example.ShoppingSystem.security.token;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
