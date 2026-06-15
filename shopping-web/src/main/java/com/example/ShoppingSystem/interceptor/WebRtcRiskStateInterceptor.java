package com.example.ShoppingSystem.interceptor;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import com.example.ShoppingSystem.filter.preauth.PreAuthBindingService;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskScope;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskSignalSupport;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskState;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskStateStore;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskStatus;
import com.example.ShoppingSystem.security.token.AuthTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
public class WebRtcRiskStateInterceptor implements HandlerInterceptor {

    private final WebRtcRiskStateStore stateStore;
    private final AdminSessionService adminSessionService;
    private final PreAuthBindingService preAuthBindingService;
    private final PreAuthRequestResolver requestResolver;
    private final WebRtcRiskSignalSupport signalSupport;
    private final AuthTokenService authTokenService;
    private final ObjectMapper objectMapper;

    public WebRtcRiskStateInterceptor(WebRtcRiskStateStore stateStore,
                                      AdminSessionService adminSessionService,
                                      PreAuthBindingService preAuthBindingService,
                                      PreAuthRequestResolver requestResolver,
                                      WebRtcRiskSignalSupport signalSupport,
                                      AuthTokenService authTokenService,
                                      ObjectMapper objectMapper) {
        this.stateStore = stateStore;
        this.adminSessionService = adminSessionService;
        this.preAuthBindingService = preAuthBindingService;
        this.requestResolver = requestResolver;
        this.signalSupport = signalSupport;
        this.authTokenService = authTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if (request == null || "OPTIONS".equalsIgnoreCase(request.getMethod()) || isReportPath(request)) {
            return true;
        }
        WebRtcRiskState state = resolveBlockingState(request);
        if (state == null) {
            return true;
        }
        writeBlockedResponse(response, request, state);
        return false;
    }

    private WebRtcRiskState resolveBlockingState(HttpServletRequest request) {
        String uri = StrUtil.blankToDefault(request.getRequestURI(), "");
        String currentHttpIp = signalSupport.normalizeIp(requestResolver.resolveClientIp(request));
        if (uri.startsWith("/shopping/admin/")) {
            return loadBlockingState(request, resolveAdminSubject(request), currentHttpIp);
        }
        WebRtcRiskState userState = loadBlockingState(request, resolveUserSubject(request), currentHttpIp);
        if (userState != null) {
            return userState;
        }
        return loadBlockingState(request, resolvePreAuthSubject(request), currentHttpIp);
    }

    private WebRtcRiskState loadBlockingState(HttpServletRequest request,
                                             Subject subject,
                                             String currentHttpIp) {
        if (subject == null || StrUtil.isBlank(subject.subjectRef())) {
            return null;
        }
        WebRtcRiskState state = stateStore.load(subject.scope(), subject.subjectRef());
        if (state == null) {
            return null;
        }
        if (!isStateForCurrentHttpIp(state, currentHttpIp)) {
            stateStore.delete(subject.scope(), subject.subjectRef());
            return null;
        }
        if (state.status() == WebRtcRiskStatus.NORMAL || !isSensitivePath(request, state)) {
            return null;
        }
        return state;
    }

    private boolean isStateForCurrentHttpIp(WebRtcRiskState state, String currentHttpIp) {
        return state != null
                && StrUtil.isNotBlank(state.httpIp())
                && StrUtil.equals(state.httpIp(), currentHttpIp);
    }

    private Subject resolveAdminSubject(HttpServletRequest request) {
        String sessionToken = adminSessionService.resolveSessionToken(request);
        return StrUtil.isBlank(sessionToken) ? null : new Subject(WebRtcRiskScope.ADMIN, sessionToken);
    }

    private Subject resolveUserSubject(HttpServletRequest request) {
        Object authUserId = request.getAttribute("authUserId");
        if (authUserId instanceof Number number) {
            return new Subject(WebRtcRiskScope.USER, String.valueOf(number.longValue()));
        }
        if (authUserId != null && StrUtil.isNotBlank(authUserId.toString())) {
            return new Subject(WebRtcRiskScope.USER, authUserId.toString().trim());
        }
        Long tokenUserId = authTokenService.tryResolveUserIdFromAccessToken(request);
        if (tokenUserId != null) {
            return new Subject(WebRtcRiskScope.USER, String.valueOf(tokenUserId));
        }
        return null;
    }

    private Subject resolvePreAuthSubject(HttpServletRequest request) {
        String preAuthToken = preAuthBindingService.resolveIncomingToken(request);
        return StrUtil.isBlank(preAuthToken) ? null : new Subject(WebRtcRiskScope.PREAUTH, preAuthToken.trim());
    }

    private boolean isSensitivePath(HttpServletRequest request, WebRtcRiskState state) {
        String uri = StrUtil.blankToDefault(request.getRequestURI(), "");
        if (uri.startsWith("/shopping/admin/")) {
            return true;
        }
        if (uri.startsWith("/shopping/")) {
            return true;
        }
        if (isOAuth2LoginEntryPath(uri)) {
            return true;
        }
        String method = StrUtil.blankToDefault(request.getMethod(), "");
        boolean safeMethod = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        return state.status() == WebRtcRiskStatus.BLOCKED && !safeMethod;
    }

    private boolean isOAuth2LoginEntryPath(String uri) {
        return uri.equals("/oauth2/github/login")
                || uri.equals("/oauth2/google/login")
                || uri.equals("/oauth2/microsoft/login");
    }

    private boolean isReportPath(HttpServletRequest request) {
        String uri = StrUtil.blankToDefault(request.getRequestURI(), "");
        return uri.equals("/shopping/auth/preauth/webrtc/report")
                || uri.equals("/shopping/auth/preauth/webrtc/state")
                || uri.equals("/shopping/admin/session/webrtc/report")
                || uri.equals("/shopping/admin/session/webrtc/state");
    }

    private void writeBlockedResponse(HttpServletResponse response,
                                      HttpServletRequest request,
                                      WebRtcRiskState state) throws IOException {
        int status = state.status() == WebRtcRiskStatus.BLOCKED
                ? HttpServletResponse.SC_FORBIDDEN
                : HttpServletResponse.SC_CONFLICT;
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", status);
        String errorCode = resolveErrorCode(state);
        body.put("error", errorCode);
        body.put("message", state.status() == WebRtcRiskStatus.BLOCKED
                ? "Network environment risk detected."
                : "Network environment verification is pending or unavailable.");
        body.put("reason", state.reason());
        body.put("path", request.getRequestURI());
        body.put("httpIp", state.httpIp());
        body.put("webRtcIps", state.webRtcIps());
        body.put("webRtcStatus", state.webRtcStatus());
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("networkCheckUrl", buildNetworkCheckFailedUrl(request, state, errorCode));
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String resolveErrorCode(WebRtcRiskState state) {
        if (state != null && StrUtil.isNotBlank(state.reason())) {
            return state.reason();
        }
        return state != null && state.status() == WebRtcRiskStatus.BLOCKED
                ? "WEBRTC_IP_MISMATCH"
                : "WEBRTC_SIGNAL_UNVERIFIED";
    }

    private String buildNetworkCheckFailedUrl(HttpServletRequest request,
                                              WebRtcRiskState state,
                                              String errorCode) {
        String scope = resolveScope(request);
        String path = buildCurrentReturnPath(request, scope);
        return "/shopping/auth/network-check-failed"
                + "?scope=" + scope
                + "&error=" + encode(errorCode)
                + "&message=" + encode(state.status() == WebRtcRiskStatus.BLOCKED
                ? "Network environment risk detected."
                : "Network environment verification is pending or unavailable.")
                + "&path=" + encode(path)
                + "&httpIp=" + encode(state.httpIp())
                + "&webRtcIps=" + encode(state.webRtcIps())
                + "&webRtcStatus=" + encode(state.webRtcStatus())
                + "&cfRay=" + encode(StrUtil.blankToDefault(request.getHeader("CF-Ray"), ""));
    }

    private String resolveScope(HttpServletRequest request) {
        String uri = StrUtil.blankToDefault(request.getRequestURI(), "");
        return uri.startsWith("/shopping/admin") ? "admin" : "user";
    }

    private String buildCurrentReturnPath(HttpServletRequest request, String scope) {
        String uri = StrUtil.blankToDefault(request.getRequestURI(), "");
        if (uri.isBlank() || !uri.startsWith("/") || uri.startsWith("//")
                || uri.startsWith("/shopping/auth/network-check-failed")) {
            return "admin".equals(scope) ? "/shopping/admin/login" : "/shopping/user/log-in";
        }
        String query = request.getQueryString();
        if (StrUtil.isBlank(query)) {
            return uri;
        }
        return uri + "?" + query;
    }

    private String encode(String value) {
        return URLEncoder.encode(StrUtil.blankToDefault(value, ""), StandardCharsets.UTF_8);
    }

    private record Subject(WebRtcRiskScope scope, String subjectRef) {
    }
}
