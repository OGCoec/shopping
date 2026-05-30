package com.example.ShoppingSystem.admin.interceptor;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class AdminSessionInterceptor implements HandlerInterceptor {

    private static final String ADMIN_LOGIN_PATH = "/shopping/admin/login";
    private static final String CONSOLE_BASE_PATH = "/shopping/admin/console";
    private static final int RETURN_TO_MAX_LENGTH = 512;

    private final AdminSessionService adminSessionService;
    private final ObjectMapper objectMapper;

    public AdminSessionInterceptor(AdminSessionService adminSessionService,
                                   ObjectMapper objectMapper) {
        this.adminSessionService = adminSessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (adminSessionService.isAuthenticated(request)) {
            return true;
        }
        if (expectsJson(request)) {
            writeJsonAuthRequired(response);
            return false;
        }
        response.sendRedirect(buildLoginRedirect(request));
        return false;
    }

    private String buildLoginRedirect(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return ADMIN_LOGIN_PATH;
        }
        String candidate = buildReturnToCandidate(request);
        if (!isAllowedConsoleReturnTo(candidate)) {
            return ADMIN_LOGIN_PATH;
        }
        return ADMIN_LOGIN_PATH + "?returnTo=" + URLEncoder.encode(candidate, StandardCharsets.UTF_8);
    }

    private String buildReturnToCandidate(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            return "";
        }
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) {
            return uri;
        }
        return uri + "?" + query;
    }

    private boolean isAllowedConsoleReturnTo(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.length() > RETURN_TO_MAX_LENGTH) {
            return false;
        }
        if (!value.startsWith("/") || value.startsWith("//")) {
            return false;
        }
        int queryIndex = value.indexOf('?');
        String path = queryIndex >= 0 ? value.substring(0, queryIndex) : value;
        return path.equals(CONSOLE_BASE_PATH) || path.startsWith(CONSOLE_BASE_PATH + "/");
    }

    private boolean expectsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        String uri = request.getRequestURI();
        if (uri != null && (uri.equals("/shopping/admin/console") || uri.startsWith("/shopping/admin/console/"))) {
            return false;
        }
        return (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (uri != null && !uri.endsWith("/console"));
    }

    private void writeJsonAuthRequired(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                AdminApiResponse.fail("ADMIN_AUTH_REQUIRED", "管理员未登录。")
        ));
    }
}
