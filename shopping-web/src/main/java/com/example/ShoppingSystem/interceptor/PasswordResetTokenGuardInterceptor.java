package com.example.ShoppingSystem.interceptor;

import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PasswordResetTokenGuardInterceptor implements HandlerInterceptor {

    private static final String RESET_PASSWORD_URL_PATH = "/shopping/user/reset-password-url";
    private static final String RESET_PASSWORD_CODE_PATH = "/shopping/user/reset-password-code";
    private static final String INVALID_LINK_LOCATION = "/shopping/user/forgot-password?notice=reset_link_invalid";

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
                && passwordResetService.isVerifiedCodeTokenUsable(token)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", INVALID_LINK_LOCATION);
        return false;
    }
}
