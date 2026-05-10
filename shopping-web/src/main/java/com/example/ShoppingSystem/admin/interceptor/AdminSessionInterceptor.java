package com.example.ShoppingSystem.admin.interceptor;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.service.AdminSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class AdminSessionInterceptor implements HandlerInterceptor {

    private static final String ADMIN_LOGIN_PATH = "/shopping/admin/login";

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
        response.sendRedirect(ADMIN_LOGIN_PATH);
        return false;
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
