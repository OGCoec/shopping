package com.example.ShoppingSystem.interceptor;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.example.ShoppingSystem.security.token.AuthUserContextHolder;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneVerifiedUserLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PhoneBindingRequiredInterceptor implements HandlerInterceptor {

    private static final int SC_PRECONDITION_REQUIRED = 428;
    private static final String ERROR_PHONE_BINDING_REQUIRED = "PHONE_BINDING_REQUIRED";
    private static final String PHONE_BINDING_MESSAGE = "Phone verification is required before continuing.";
    private static final String SECURITY_PHONE_PATH = "/shopping/user/security/phone";

    private final PhoneVerifiedUserLookupService phoneVerifiedUserLookupService;
    private final ObjectMapper objectMapper;

    public PhoneBindingRequiredInterceptor(PhoneVerifiedUserLookupService phoneVerifiedUserLookupService,
                                           ObjectMapper objectMapper) {
        this.phoneVerifiedUserLookupService = phoneVerifiedUserLookupService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || shouldSkip(request)) {
            return true;
        }

        String riskLevel = resolveRiskLevel(request);
        if (!requiresPhoneBinding(riskLevel)) {
            return true;
        }
        if ("L6".equals(riskLevel)) {
            writeJsonError(response, request, HttpServletResponse.SC_FORBIDDEN, "RISK_BLOCKED",
                    "Current risk level is too high. Please try again later.", riskLevel);
            return false;
        }

        Long userId = resolveUserId(request);
        if (userId == null) {
            writeJsonError(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_REQUIRED", "Authentication is required.", riskLevel);
            return false;
        }
        if (phoneVerifiedUserLookupService.isPhoneVerified(userId)) {
            return true;
        }

        writeJsonError(response, request, SC_PRECONDITION_REQUIRED,
                ERROR_PHONE_BINDING_REQUIRED, PHONE_BINDING_MESSAGE, riskLevel);
        return false;
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String uri = request == null ? "" : StrUtil.blankToDefault(request.getRequestURI(), "");
        return uri.startsWith(SECURITY_PHONE_PATH)
                || uri.startsWith("/shopping/user/auth/")
                || uri.startsWith("/shopping/user/login/")
                || uri.startsWith("/shopping/user/register/")
                || uri.startsWith("/shopping/auth/");
    }

    private String resolveRiskLevel(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute("preAuthRiskLevel");
        if (value instanceof String text) {
            return normalizeRiskLevel(text);
        }
        AuthUserContext context = AuthUserContextHolder.get();
        return normalizeRiskLevel(context == null ? null : context.riskLevel());
    }

    private boolean requiresPhoneBinding(String riskLevel) {
        return "L3".equals(riskLevel)
                || "L4".equals(riskLevel)
                || "L5".equals(riskLevel)
                || "L6".equals(riskLevel);
    }

    private Long resolveUserId(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute("authUserId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            Long parsed = parseLong(text);
            if (parsed != null) {
                return parsed;
            }
        }
        AuthUserContext context = AuthUserContextHolder.get();
        return context == null ? null : context.userId();
    }

    private Long parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = StrUtil.blankToDefault(riskLevel, "").trim().toUpperCase();
        return switch (normalized) {
            case "L1", "L2", "L3", "L4", "L5", "L6" -> normalized;
            default -> "L1";
        };
    }

    private void writeJsonError(HttpServletResponse response,
                                HttpServletRequest request,
                                int status,
                                String error,
                                String message,
                                String riskLevel) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("riskLevel", riskLevel);
        body.put("redirectPath", SECURITY_PHONE_PATH);
        body.put("path", request == null ? null : request.getRequestURI());
        body.put("timestamp", OffsetDateTime.now().toString());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
