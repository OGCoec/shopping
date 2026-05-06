package com.example.ShoppingSystem.interceptor;

import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PasswordResetTokenGuardInterceptor implements HandlerInterceptor {

    private static final String RESET_PASSWORD_URL_PATH = "/shopping/user/reset-password-url";
    private static final String RESET_PASSWORD_CODE_PATH = "/shopping/user/reset-password-code";
    private static final String SESSION_ENDED_LOCATION = "/shopping/user/session-ended";
    private static final String PASSWORD_RESET_CODE_TOKEN_COOKIE = "PASSWORD_RESET_CODE_TOKEN";
    private static final String PASSWORD_RESET_COOKIE_PATH = "/shopping/user";
    private static final String PREAUTH_TOKEN_ATTRIBUTE = "preAuthToken";
    private static final String PREAUTH_TOKEN_COOKIE = "PREAUTH_TOKEN";

    private final PasswordResetService passwordResetService;

    public PasswordResetTokenGuardInterceptor(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String requestPath = request.getRequestURI();
        String token = request.getParameter("token");
        if (RESET_PASSWORD_URL_PATH.equals(requestPath)
                && passwordResetService.isResetLinkTokenUsable(token)) {
            return true;
        }
        if (RESET_PASSWORD_CODE_PATH.equals(requestPath)
                && passwordResetService.isVerifiedCodeTokenUsable(
                resolveCookieValue(request, PASSWORD_RESET_CODE_TOKEN_COOKIE),
                resolvePreAuthToken(request))) {
            return true;
        }
        redirectToSessionEnded(response);
        return false;
    }

    private void redirectToSessionEnded(HttpServletResponse response) {
        if (response.isCommitted()) {
            return;
        }
        response.addHeader("Set-Cookie", PASSWORD_RESET_CODE_TOKEN_COOKIE
                + "=; Path=" + PASSWORD_RESET_COOKIE_PATH
                + "; Max-Age=0; HttpOnly; Secure; SameSite=Lax");
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", SESSION_ENDED_LOCATION);
    }

    private String resolveCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request == null ? null : request.getCookies();
        if (cookies == null) {
            return "";
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return "";
    }

    private String resolvePreAuthToken(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(PREAUTH_TOKEN_ATTRIBUTE);
        if (value instanceof String token && !token.isBlank()) {
            return token.trim();
        }
        return resolveCookieValue(request, PREAUTH_TOKEN_COOKIE);
    }
}
