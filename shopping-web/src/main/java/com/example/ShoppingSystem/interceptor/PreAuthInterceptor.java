package com.example.ShoppingSystem.interceptor;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthValidationOutcome;
import com.example.ShoppingSystem.security.token.AuthTokenService;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceRiskProfileWriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PreAuthInterceptor implements HandlerInterceptor {

    private static final String BLOCKED_PUBLIC_ERROR_CODE = "PREAUTH_RETRY_LATER";
    private static final String BLOCKED_PUBLIC_ERROR_MESSAGE = "当前操作过于频繁，请稍后重试";

    private static final String SCENE_PREAUTH_IP_CHANGED_WAF_REQUIRED = "PREAUTH_IP_CHANGED_WAF_REQUIRED";

    private final PreAuthBindingService preAuthBindingService;
    private final ObjectMapper objectMapper;
    private final AuthTokenService authTokenService;
    private final DeviceRiskProfileWriteService deviceRiskProfileWriteService;

    public PreAuthInterceptor(PreAuthBindingService preAuthBindingService,
                              ObjectMapper objectMapper,
                              AuthTokenService authTokenService,
                              DeviceRiskProfileWriteService deviceRiskProfileWriteService) {
        this.preAuthBindingService = preAuthBindingService;
        this.objectMapper = objectMapper;
        this.authTokenService = authTokenService;
        this.deviceRiskProfileWriteService = deviceRiskProfileWriteService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if (!preAuthBindingService.isEnabled() || shouldSkip(request)) {
            return true;
        }

        String token = preAuthBindingService.resolveIncomingToken(request);
        if (StrUtil.isBlank(token)) {
            writeJsonError(
                    response,
                    request,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "PREAUTH_MISSING",
                    "预登录状态缺失，请刷新页面后重试",
                    null
            );
            return false;
        }

        PreAuthValidationOutcome outcome = preAuthBindingService.validateAndTouch(
                token,
                request.getHeader(PreAuthHeaders.HEADER_DEVICE_FINGERPRINT),
                request
        );
        if (!outcome.valid()) {
            switch (outcome.error()) {
                case EXPIRED -> {
                    refreshExpiredCookie(response, request);
                    writeJsonError(
                            response,
                            request,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "PREAUTH_EXPIRED",
                            "预登录状态已过期，请刷新页面后重试",
                            null
                    );
                }
                case FINGERPRINT_MISMATCH -> {
                    refreshExpiredCookie(response, request);
                    writeJsonError(
                            response,
                            request,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "PREAUTH_FINGERPRINT_MISMATCH",
                            "当前设备环境发生变化，请刷新页面后重试",
                            null
                    );
                }
                case USER_AGENT_MISMATCH -> {
                    refreshExpiredCookie(response, request);
                    writeJsonError(
                            response,
                            request,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "PREAUTH_UA_MISMATCH",
                            "当前浏览器环境发生变化，请刷新页面后重试",
                            null
                    );
                }
                case IP_CHANGED_WAF_REQUIRED -> {
                    recordIpChangedWafRequired(request);
                    writeWafVerificationRequired(response, request);
                }
                default -> {
                    refreshExpiredCookie(response, request);
                    writeJsonError(
                            response,
                            request,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "PREAUTH_INVALID",
                            "预登录状态无效，请刷新页面后重试",
                            null
                    );
                }
            }
            return false;
        }

        PreAuthBinding binding = outcome.binding();
        refreshTokenCookie(response, request, binding.token());

        if (preAuthBindingService.isBlockedRisk(binding.riskLevel())
                && !isLoginFlowApiRequest(request)) {
            writeJsonError(
                    response,
                    request,
                    HttpServletResponse.SC_FORBIDDEN,
                    BLOCKED_PUBLIC_ERROR_CODE,
                    BLOCKED_PUBLIC_ERROR_MESSAGE,
                    null
            );
            return false;
        }

        request.setAttribute("preAuthToken", binding.token());
        request.setAttribute("preAuthRiskLevel", binding.riskLevel());
        request.setAttribute("preAuthIpScore", binding.ipScore());
        request.setAttribute("preAuthDeviceScore", binding.deviceScore());
        request.setAttribute("preAuthScore", binding.score());
        return true;
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return StrUtil.isBlank(uri) || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private boolean isLoginFlowApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/shopping/user/login/");
    }

    private void recordIpChangedWafRequired(HttpServletRequest request) {
        deviceRiskProfileWriteService.recordFailure(
                authTokenService.tryResolveUserIdFromAccessToken(request),
                request == null ? "" : request.getHeader(PreAuthHeaders.HEADER_DEVICE_FINGERPRINT),
                preAuthBindingService.resolveClientIp(request),
                SCENE_PREAUTH_IP_CHANGED_WAF_REQUIRED
        );
    }

    private void refreshTokenCookie(HttpServletResponse response,
                                    HttpServletRequest request,
                                    String token) {
        response.addHeader("Set-Cookie", preAuthBindingService.buildTokenCookie(token, request).toString());
    }

    private void refreshExpiredCookie(HttpServletResponse response,
                                      HttpServletRequest request) {
        response.addHeader("Set-Cookie", preAuthBindingService.buildExpiredTokenCookie(request).toString());
    }

    private void writeJsonError(HttpServletResponse response,
                                HttpServletRequest request,
                                int status,
                                String errorCode,
                                String message,
                                PreAuthBinding binding) throws IOException {
        writeJsonError(response, request, status, errorCode, message, binding, null);
    }

    private void writeJsonError(HttpServletResponse response,
                                HttpServletRequest request,
                                int status,
                                String errorCode,
                                String message,
                                PreAuthBinding binding,
                                String verifyUrl) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", status);
        body.put("error", errorCode);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        body.put("timestamp", OffsetDateTime.now().toString());
        if (binding != null) {
            body.put("riskLevel", binding.riskLevel());
        }
        if (verifyUrl != null && !verifyUrl.isBlank()) {
            body.put("verifyUrl", verifyUrl);
        }
        objectMapper.writeValue(response.getWriter(), body);
    }

    private void writeWafVerificationRequired(HttpServletResponse response,
                                              HttpServletRequest request) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.addHeader("Set-Cookie", preAuthBindingService.buildWafRequiredCookie(request).toString());
        boolean htmlNavigation = isHtmlNavigationRequest(request);
        String verifyUrl = htmlNavigation
                ? buildWafVerifyUrlFromCurrentRequest(request)
                : buildWafVerifyUrlFromReferer(request);
        if (htmlNavigation) {
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader("Location", verifyUrl);
            return;
        }

        writeJsonError(
                response,
                request,
                HttpServletResponse.SC_CONFLICT,
                "PREAUTH_IP_CHANGED_WAF_REQUIRED",
                "当前网络环境发生变化，请先完成安全验证",
                null,
                verifyUrl
        );
    }

    private String buildWafVerifyUrlFromCurrentRequest(HttpServletRequest request) {
        String returnPath = request.getRequestURI();
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            returnPath += "?" + query;
        }
        return buildWafVerifyUrl(returnPath);
    }

    private String buildWafVerifyUrlFromReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI refererUri = URI.create(referer.trim());
            String path = refererUri.getPath();
            if (path == null || path.isBlank() || !path.startsWith("/") || path.startsWith("//")) {
                return null;
            }
            if (path.startsWith("/shopping/auth/waf/verify")) {
                return null;
            }
            String query = refererUri.getQuery();
            String returnPath = (query == null || query.isBlank()) ? path : path + "?" + query;
            return buildWafVerifyUrl(returnPath);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildWafVerifyUrl(String returnPath) {
        if (returnPath == null || returnPath.isBlank()) {
            return "/shopping/auth/waf/verify";
        }
        return "/shopping/auth/waf/verify?"
                + "return="
                + URLEncoder.encode(returnPath, StandardCharsets.UTF_8);
    }

    private boolean isHtmlNavigationRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }
}
