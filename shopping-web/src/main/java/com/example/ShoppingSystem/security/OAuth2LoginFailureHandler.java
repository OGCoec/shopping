package com.example.ShoppingSystem.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 登录失败处理器。
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final String LOGIN_PAGE_URL = "https://localhost:6655/shopping/user/login";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String message = URLEncoder.encode("oauth_failed", StandardCharsets.UTF_8);
        response.sendRedirect(LOGIN_PAGE_URL + "?oauth=failed&msg=" + message);
    }
}
