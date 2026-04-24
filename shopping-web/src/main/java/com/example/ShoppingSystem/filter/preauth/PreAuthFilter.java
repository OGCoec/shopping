package com.example.ShoppingSystem.filter.preauth;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pre-auth gate for anonymous register APIs.
 */
public class PreAuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_GET_PATHS = Set.of(
            "/",
            "/index.html",
            "/shopping/user/login",
            "/shopping/user/register",
            "/shopping/user/forgot-password",
            "/swagger-ui.html",
            "/doc.html",
            "/error",
            "/oauth2/github/login",
            "/oauth2/google/login",
            "/oauth2/microsoft/login"
    );

    private static final Set<String> PUBLIC_ANY_METHOD_PATHS = Set.of(
            "/shopping/auth/preauth/bootstrap",
            "/shopping/auth/preauth/phone-country",
            "/favicon.ico"
    );

    private static final String[] PUBLIC_PATH_PREFIXES = {
            "/css/",
            "/js/",
            "/images/",
            "/fragments/",
            "/webjars/",
            "/v3/api-docs",
            "/swagger-ui/",
            "/oauth2/authorization/",
            "/login/oauth2/code/"
    };

    private final PreAuthBindingService preAuthBindingService;
    private final ObjectMapper objectMapper;

    public PreAuthFilter(PreAuthBindingService preAuthBindingService,
                         ObjectMapper objectMapper) {
        this.preAuthBindingService = preAuthBindingService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return true;
        }

        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        if (PUBLIC_ANY_METHOD_PATHS.contains(uri)) {
            return true;
        }

        if (isGetLikeMethod(method) && PUBLIC_GET_PATHS.contains(uri)) {
            return true;
        }

        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    private boolean isGetLikeMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!preAuthBindingService.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = preAuthBindingService.resolveIncomingToken(request);
        if (StrUtil.isBlank(token)) {
            writeJsonError(response, request, HttpServletResponse.SC_UNAUTHORIZED, "PREAUTH_MISSING", "预登录令牌缺失", null);
            return;
        }

        PreAuthBindingService.ValidationOutcome outcome = preAuthBindingService.validateAndTouch(
                token,
                request.getHeader(PreAuthHeaders.HEADER_DEVICE_FINGERPRINT),
                request
        );
        if (!outcome.valid()) {
            refreshExpiredCookie(response, request);
            switch (outcome.error()) {
                case EXPIRED ->
                        writeJsonError(response, request, HttpServletResponse.SC_UNAUTHORIZED, "PREAUTH_EXPIRED", "预登录令牌不存在或已过期", null);
                case FINGERPRINT_MISMATCH ->
                        writeJsonError(response, request, HttpServletResponse.SC_UNAUTHORIZED, "PREAUTH_FINGERPRINT_MISMATCH", "设备指纹不匹配", null);
                case USER_AGENT_MISMATCH ->
                        writeJsonError(response, request, HttpServletResponse.SC_UNAUTHORIZED, "PREAUTH_UA_MISMATCH", "设备环境已变化，请重新初始化", null);
                default ->
                        writeJsonError(response, request, HttpServletResponse.SC_UNAUTHORIZED, "PREAUTH_INVALID", "预登录状态无效", null);
            }
            return;
        }

        PreAuthBindingService.PreAuthBinding binding = outcome.binding();
        refreshTokenCookie(response, request, binding.token());

        if (preAuthBindingService.isBlockedRisk(binding.riskLevel())) {
            writeJsonError(response, request, HttpServletResponse.SC_FORBIDDEN, "PREAUTH_IP_BLOCKED", "当前网络环境风险过高，暂时禁止访问", binding);
            return;
        }

        request.setAttribute("preAuthToken", binding.token());
        request.setAttribute("preAuthRiskLevel", binding.riskLevel());
        filterChain.doFilter(request, response);
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
                                PreAuthBindingService.PreAuthBinding binding) throws IOException {
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
        objectMapper.writeValue(response.getWriter(), body);
    }
}
