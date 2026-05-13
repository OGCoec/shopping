package com.example.ShoppingSystem.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OAuth2PreAuthRiskFilter extends OncePerRequestFilter {

    private static final String NETWORK_CHECK_FAILED_PATH = "/shopping/auth/network-check-failed";

    private final OAuth2PreAuthRiskGuard riskGuard;
    private final ObjectMapper objectMapper;

    public OAuth2PreAuthRiskFilter(OAuth2PreAuthRiskGuard riskGuard,
                                   ObjectMapper objectMapper) {
        this.riskGuard = riskGuard;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!shouldGuard(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        OAuth2PreAuthRiskDecision decision = riskGuard.evaluate(request);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeBlockedResponse(request, response, decision);
    }

    private boolean shouldGuard(HttpServletRequest request) {
        String path = normalizedPath(request);
        return path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/code/");
    }

    private String normalizedPath(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri != null && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri == null ? "" : uri;
    }

    private void writeBlockedResponse(HttpServletRequest request,
                                      HttpServletResponse response,
                                      OAuth2PreAuthRiskDecision decision) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        if (acceptsJson(request)) {
            response.setStatus(decision.status());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), jsonBody(request, decision));
            return;
        }
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", buildBlockedPageUrl(request, decision));
    }

    private boolean acceptsJson(HttpServletRequest request) {
        String accept = request == null ? "" : request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private Map<String, Object> jsonBody(HttpServletRequest request, OAuth2PreAuthRiskDecision decision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", decision.status());
        body.put("error", decision.error());
        body.put("message", decision.message());
        body.put("path", request == null ? "" : request.getRequestURI());
        body.put("timestamp", OffsetDateTime.now().toString());
        return body;
    }

    private String buildBlockedPageUrl(HttpServletRequest request, OAuth2PreAuthRiskDecision decision) {
        return NETWORK_CHECK_FAILED_PATH
                + "?scope=user"
                + "&error=" + encode(decision.error())
                + "&message=" + encode(decision.message())
                + "&path=" + encode(request == null ? "" : request.getRequestURI())
                + "&cfRay=" + encode(header(request, "CF-Ray"));
    }

    private String header(HttpServletRequest request, String name) {
        return request == null ? "" : request.getHeader(name);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
