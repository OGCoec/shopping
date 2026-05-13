package com.example.ShoppingSystem.interceptor;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.domain.WebRtcIpConsistencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WebRtcIpConsistencyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebRtcIpConsistencyInterceptor.class);

    private final WebRtcIpConsistencyService webRtcIpConsistencyService;
    private final ObjectMapper objectMapper;

    public WebRtcIpConsistencyInterceptor(WebRtcIpConsistencyService webRtcIpConsistencyService,
                                          ObjectMapper objectMapper) {
        this.webRtcIpConsistencyService = webRtcIpConsistencyService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if (request == null || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        WebRtcIpConsistencyService.CheckResult result = webRtcIpConsistencyService.checkAndPersist(request);
        if (result.allowed()) {
            return true;
        }

        log.warn("WebRTC IP consistency blocked request: errorCode={}, method={}, uri={}, httpIp={}, webRtcIp={}, webRtcStatus={}",
                result.errorCode(),
                request.getMethod(),
                request.getRequestURI(),
                result.httpIp(),
                result.webRtcIp(),
                result.webRtcStatus());

        if (isHtmlNavigationRequest(request)) {
            response.sendRedirect(buildNetworkCheckFailedUrl(request, result));
            return false;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), buildErrorBody(request, result));
        return false;
    }

    private Map<String, Object> buildErrorBody(HttpServletRequest request,
                                               WebRtcIpConsistencyService.CheckResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", HttpServletResponse.SC_FORBIDDEN);
        body.put("error", result.errorCode());
        body.put("message", result.message());
        body.put("path", request.getRequestURI());
        body.put("httpIp", result.httpIp());
        body.put("webRtcIp", result.webRtcIp());
        body.put("webRtcStatus", result.webRtcStatus());
        body.put("cfRay", StrUtil.blankToDefault(request.getHeader("CF-Ray"), ""));
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("networkCheckUrl", buildNetworkCheckFailedUrl(request, result));
        return body;
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

    private String buildNetworkCheckFailedUrl(HttpServletRequest request,
                                              WebRtcIpConsistencyService.CheckResult result) {
        String scope = resolveScope(request);
        String path = buildCurrentReturnPath(request, scope);
        return "/shopping/auth/network-check-failed"
                + "?scope=" + scope
                + "&error=" + encode(result.errorCode())
                + "&message=" + encode(result.message())
                + "&path=" + encode(path)
                + "&httpIp=" + encode(result.httpIp())
                + "&webRtcIp=" + encode(result.webRtcIp())
                + "&webRtcStatus=" + encode(result.webRtcStatus())
                + "&cfRay=" + encode(StrUtil.blankToDefault(request.getHeader("CF-Ray"), ""));
    }

    private String encode(String value) {
        return URLEncoder.encode(StrUtil.blankToDefault(value, ""), StandardCharsets.UTF_8);
    }

    private String resolveScope(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/shopping/admin") ? "admin" : "user";
    }

    private String buildCurrentReturnPath(HttpServletRequest request, String scope) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank() || !uri.startsWith("/") || uri.startsWith("//")
                || uri.startsWith("/shopping/auth/network-check-failed")) {
            return "admin".equals(scope) ? "/shopping/admin/login" : "/shopping/user/log-in";
        }
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return uri;
        }
        return uri + "?" + query;
    }
}
