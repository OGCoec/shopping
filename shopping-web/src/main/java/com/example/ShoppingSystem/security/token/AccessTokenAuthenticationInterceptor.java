package com.example.ShoppingSystem.security.token;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

@Component
public class AccessTokenAuthenticationInterceptor implements HandlerInterceptor {

    private static final String LOGIN_PATH = "/shopping/user/log-in";

    private final AuthTokenService authTokenService;
    private final ObjectMapper objectMapper;

    public AccessTokenAuthenticationInterceptor(AuthTokenService authTokenService,
                                                ObjectMapper objectMapper) {
        this.authTokenService = authTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        try {
            String accessToken = authTokenService.resolveAccessToken(request);
            if (StrUtil.isBlank(accessToken)) {
                if (redirectLoginForHtmlNavigation(response, request)) {
                    return false;
                }
                writeJsonError(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                        "AUTH_REQUIRED", "Authentication is required.");
                return false;
            }

            String riskLevel = request.getAttribute("preAuthRiskLevel") instanceof String text ? text : "L1";
            AuthUserContext context = authTokenService.authenticateAccessToken(accessToken, riskLevel);
            AuthUserContextHolder.set(context);
            request.setAttribute("authUserContext", context);
            request.setAttribute("authUserId", context.userId());

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    context,
                    null,
                    authorities(context)
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return true;
        } catch (Exception e) {
            clearAuthenticationContext();
            Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ExpiredJwtException) {
                if (redirectLoginForHtmlNavigation(response, request)) {
                    return false;
                }
                writeJsonError(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                        "ACCESS_TOKEN_EXPIRED", "Access token has expired.");
                return false;
            }
            if (cause instanceof AuthTokenException authError) {
                if (redirectLoginForHtmlNavigation(response, request)) {
                    return false;
                }
                writeJsonError(response, request, authError.status(), authError.error(), authError.getMessage());
                return false;
            }
            if (redirectLoginForHtmlNavigation(response, request)) {
                return false;
            }
            writeJsonError(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                    "ACCESS_TOKEN_INVALID", "Access token is invalid.");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        clearAuthenticationContext();
    }

    private Set<SimpleGrantedAuthority> authorities(AuthUserContext context) {
        Set<String> roles = context.roles() == null ? Set.of() : context.roles();
        return roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void clearAuthenticationContext() {
        AuthUserContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private boolean redirectLoginForHtmlNavigation(HttpServletResponse response,
                                                   HttpServletRequest request) throws IOException {
        if (!isHtmlNavigationRequest(request) || response.isCommitted()) {
            return false;
        }
        response.sendRedirect(LOGIN_PATH);
        return true;
    }

    private boolean isHtmlNavigationRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        String requestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private void writeJsonError(HttpServletResponse response,
                                HttpServletRequest request,
                                int status,
                                String error,
                                String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        body.put("timestamp", OffsetDateTime.now().toString());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
