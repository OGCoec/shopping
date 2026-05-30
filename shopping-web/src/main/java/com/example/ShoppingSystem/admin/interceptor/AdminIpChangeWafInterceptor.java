package com.example.ShoppingSystem.admin.interceptor;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import com.example.ShoppingSystem.admin.service.auth.AdminWafVerificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AdminIpChangeWafInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminIpChangeWafInterceptor.class);
    private static final String ADMIN_CONSOLE_PATH = "/shopping/admin/console";
    private static final String WAF_VERIFY_PATH = "/shopping/auth/waf/verify";

    private final AdminSessionService adminSessionService;
    private final AdminWafVerificationService adminWafVerificationService;
    private final ObjectMapper objectMapper;

    public AdminIpChangeWafInterceptor(AdminSessionService adminSessionService,
                                       AdminWafVerificationService adminWafVerificationService,
                                       ObjectMapper objectMapper) {
        this.adminSessionService = adminSessionService;
        this.adminWafVerificationService = adminWafVerificationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if (request == null || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!adminSessionService.isAuthenticated(request)) {
            return true;
        }

        String currentIp = adminSessionService.resolveClientIp(request);
        if (adminSessionService.isCurrentIpAllowed(request, currentIp)) {
            return true;
        }

        if (adminWafVerificationService.consumeVerifiedTicket(request)) {
            adminSessionService.refreshCurrentIp(request, currentIp);
            response.addHeader("Set-Cookie", adminWafVerificationService.buildClearVerifiedCookie(request).toString());
            log.info("Admin IP change WAF verified: method={}, uri={}, currentIp={}",
                    request.getMethod(), request.getRequestURI(), currentIp);
            return true;
        }

        log.warn("Admin IP change requires WAF: method={}, uri={}, currentIp={}, xForwardedFor={}, xRealIp={}, remoteAddr={}",
                request.getMethod(),
                request.getRequestURI(),
                currentIp,
                header(request, "X-Forwarded-For"),
                header(request, "X-Real-IP"),
                request.getRemoteAddr());
        writeWafRequired(response, request);
        return false;
    }

    private void writeWafRequired(HttpServletResponse response,
                                  HttpServletRequest request) throws IOException {
        String verifyUrl = isHtmlNavigationRequest(request)
                ? buildWafVerifyUrl(buildCurrentReturnPath(request))
                : buildWafVerifyUrl(resolveRefererReturnPath(request));
        if (isHtmlNavigationRequest(request)) {
            response.sendRedirect(verifyUrl);
            return;
        }

        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", HttpServletResponse.SC_CONFLICT);
        body.put("error", AdminWafVerificationService.ADMIN_WAF_REQUIRED_ERROR_CODE);
        body.put("code", AdminWafVerificationService.ADMIN_WAF_REQUIRED_ERROR_CODE);
        body.put("message", AdminWafVerificationService.ADMIN_WAF_REQUIRED_MESSAGE);
        body.put("path", request.getRequestURI());
        body.put("verifyUrl", verifyUrl);
        body.put("timestamp", OffsetDateTime.now().toString());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String resolveRefererReturnPath(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (StrUtil.isBlank(referer)) {
            return ADMIN_CONSOLE_PATH;
        }
        try {
            URI refererUri = URI.create(referer.trim());
            String path = refererUri.getPath();
            if (!isAllowedAdminReturnPath(path)) {
                return ADMIN_CONSOLE_PATH;
            }
            String query = refererUri.getQuery();
            return StrUtil.isBlank(query) ? path : path + "?" + query;
        } catch (Exception ignored) {
            return ADMIN_CONSOLE_PATH;
        }
    }

    private String buildCurrentReturnPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!isAllowedAdminReturnPath(uri)) {
            return ADMIN_CONSOLE_PATH;
        }
        String query = request.getQueryString();
        return StrUtil.isBlank(query) ? uri : uri + "?" + query;
    }

    private boolean isAllowedAdminReturnPath(String path) {
        return StrUtil.isNotBlank(path)
                && path.startsWith("/")
                && !path.startsWith("//")
                && (path.equals(ADMIN_CONSOLE_PATH) || path.startsWith(ADMIN_CONSOLE_PATH + "/"));
    }

    private String buildWafVerifyUrl(String returnPath) {
        String target = isAllowedAdminReturnPath(stripQuery(returnPath)) ? returnPath : ADMIN_CONSOLE_PATH;
        return WAF_VERIFY_PATH + "?return=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
    }

    private String stripQuery(String path) {
        if (path == null) {
            return "";
        }
        int queryIndex = path.indexOf('?');
        return queryIndex >= 0 ? path.substring(0, queryIndex) : path;
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

    private String header(HttpServletRequest request, String name) {
        return request == null ? "" : StrUtil.blankToDefault(request.getHeader(name), "");
    }
}
