package com.example.ShoppingSystem.interceptor;

import com.example.ShoppingSystem.security.risk.AccountNetworkRiskService;
import com.example.ShoppingSystem.security.token.AuthTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PostLoginAccountNetworkRiskInterceptor implements HandlerInterceptor {

    private static final int HTTP_LOCKED = 423;

    private final AccountNetworkRiskService accountNetworkRiskService;
    private final AuthTokenService authTokenService;
    private final ObjectMapper objectMapper;

    public PostLoginAccountNetworkRiskInterceptor(AccountNetworkRiskService accountNetworkRiskService,
                                                  AuthTokenService authTokenService,
                                                  ObjectMapper objectMapper) {
        this.accountNetworkRiskService = accountNetworkRiskService;
        this.authTokenService = authTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        AccountNetworkRiskService.AccountNetworkRiskDecision decision =
                accountNetworkRiskService.evaluate(request);
        if (decision.allowed()) {
            return true;
        }

        authTokenService.clearAuthCookies(response, request);
        int status = decision.terminationRequired() ? HttpServletResponse.SC_FORBIDDEN : HTTP_LOCKED;
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), buildErrorBody(request, decision, status));
        return false;
    }

    private Map<String, Object> buildErrorBody(HttpServletRequest request,
                                               AccountNetworkRiskService.AccountNetworkRiskDecision decision,
                                               int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", status);
        body.put("error", decision.terminationRequired()
                ? "ACCOUNT_TERMINATION_REQUIRED"
                : "ACCOUNT_NETWORK_RISK_LOCKED");
        body.put("message", decision.message());
        body.put("reason", decision.reason());
        body.put("accountStatus", decision.status());
        body.put("terminationRequired", decision.terminationRequired());
        body.put("retryAfterMs", decision.retryAfterMs());
        body.put("path", request == null ? "" : request.getRequestURI());
        body.put("timestamp", OffsetDateTime.now().toString());
        return body;
    }
}
