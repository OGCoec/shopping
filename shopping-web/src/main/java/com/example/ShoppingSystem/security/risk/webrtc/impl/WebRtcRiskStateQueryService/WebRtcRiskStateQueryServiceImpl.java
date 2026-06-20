package com.example.ShoppingSystem.security.risk.webrtc.impl.WebRtcRiskStateQueryService;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import com.example.ShoppingSystem.security.token.AuthTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskStateQueryService;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskScope;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskSignalSupport;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskState;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskStateResponse;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskStateStore;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskStatus;
@Service
public class WebRtcRiskStateQueryServiceImpl implements WebRtcRiskStateQueryService {

    private static final String REASON_MATCH = "WEBRTC_IP_MATCH";
    private static final String REASON_NO_SUBJECT = "WEBRTC_STATE_NO_SUBJECT";
    private static final String REASON_STATE_MISSING = "WEBRTC_STATE_MISSING";
    private static final String REASON_STATE_EXPIRED = "WEBRTC_STATE_EXPIRED";
    private static final String REASON_STATUS_NOT_NORMAL = "WEBRTC_STATE_STATUS_NOT_NORMAL";
    private static final String REASON_SIGNAL_UNVERIFIED = "WEBRTC_SIGNAL_UNVERIFIED";
    private static final String REASON_HTTP_IP_UNVERIFIED = "WEBRTC_HTTP_IP_UNVERIFIED";
    private static final String REASON_HTTP_IP_CHANGED = "WEBRTC_HTTP_IP_CHANGED";

    private final WebRtcRiskStateStore stateStore;
    private final WebRtcRiskSignalSupport signalSupport;
    private final PreAuthRequestResolver requestResolver;
    private final AuthTokenService authTokenService;
    private final AdminSessionService adminSessionService;

    public WebRtcRiskStateQueryServiceImpl(WebRtcRiskStateStore stateStore,
                                       WebRtcRiskSignalSupport signalSupport,
                                       PreAuthRequestResolver requestResolver,
                                       AuthTokenService authTokenService,
                                       AdminSessionService adminSessionService) {
        this.stateStore = stateStore;
        this.signalSupport = signalSupport;
        this.requestResolver = requestResolver;
        this.authTokenService = authTokenService;
        this.adminSessionService = adminSessionService;
    }

    public WebRtcRiskStateResponse queryAdmin(HttpServletRequest request) {
        String currentHttpIp = currentHttpIp(request);
        String sessionToken = adminSessionService.resolveSessionToken(request);
        if (StrUtil.isBlank(sessionToken)) {
            return WebRtcRiskStateResponse.notReusable(WebRtcRiskScope.ADMIN, currentHttpIp, 0L, REASON_NO_SUBJECT);
        }
        return querySubject(WebRtcRiskScope.ADMIN, sessionToken.trim(), currentHttpIp);
    }

    public WebRtcRiskStateResponse queryPreAuthOrUser(HttpServletRequest request) {
        String currentHttpIp = currentHttpIp(request);
        WebRtcRiskStateResponse userResponse = null;
        Long userId = authTokenService.tryResolveUserIdFromAccessToken(request);
        if (userId != null) {
            userResponse = querySubject(WebRtcRiskScope.USER, String.valueOf(userId), currentHttpIp);
            if (userResponse.reusable()) {
                return userResponse;
            }
        }

        String preAuthToken = requestResolver.resolveIncomingToken(request);
        if (StrUtil.isNotBlank(preAuthToken)) {
            WebRtcRiskStateResponse preAuthResponse =
                    querySubject(WebRtcRiskScope.PREAUTH, preAuthToken.trim(), currentHttpIp);
            if (preAuthResponse.reusable() || userResponse == null) {
                return preAuthResponse;
            }
        }
        if (userResponse != null) {
            return userResponse;
        }
        return WebRtcRiskStateResponse.notReusable(WebRtcRiskScope.PREAUTH, currentHttpIp, 0L, REASON_NO_SUBJECT);
    }

    private WebRtcRiskStateResponse querySubject(WebRtcRiskScope scope, String subjectRef, String currentHttpIp) {
        if (scope == null || StrUtil.isBlank(subjectRef)) {
            return WebRtcRiskStateResponse.notReusable(scope, currentHttpIp, 0L, REASON_NO_SUBJECT);
        }
        WebRtcRiskState state = stateStore.load(scope, subjectRef);
        if (state == null) {
            return WebRtcRiskStateResponse.notReusable(scope, currentHttpIp, 0L, REASON_STATE_MISSING);
        }
        long ttlMillis = stateStore.ttlMillis(scope, subjectRef);
        if (ttlMillis <= 0L) {
            return WebRtcRiskStateResponse.notReusable(scope, currentHttpIp, ttlMillis, REASON_STATE_EXPIRED);
        }
        if (state.status() != WebRtcRiskStatus.NORMAL) {
            return WebRtcRiskStateResponse.notReusable(scope, currentHttpIp, ttlMillis, REASON_STATUS_NOT_NORMAL);
        }
        String webRtcStatus = normalizeStatus(state.webRtcStatus());
        List<String> webRtcIps = parseWebRtcIps(state.webRtcIps());
        if (!"ok".equals(webRtcStatus) || webRtcIps.isEmpty()) {
            return WebRtcRiskStateResponse.notReusable(scope, currentHttpIp, ttlMillis, REASON_SIGNAL_UNVERIFIED);
        }
        if (StrUtil.isBlank(currentHttpIp)) {
            return WebRtcRiskStateResponse.notReusable(scope, currentHttpIp, ttlMillis, REASON_HTTP_IP_UNVERIFIED);
        }
        String stateHttpIp = signalSupport.normalizeIp(state.httpIp());
        if (!StrUtil.equals(stateHttpIp, currentHttpIp)) {
            return WebRtcRiskStateResponse.notReusable(scope, currentHttpIp, ttlMillis, REASON_HTTP_IP_CHANGED);
        }
        return WebRtcRiskStateResponse.reusable(
                scope,
                currentHttpIp,
                webRtcIps,
                webRtcStatus,
                ttlMillis,
                REASON_MATCH
        );
    }

    private String currentHttpIp(HttpServletRequest request) {
        return signalSupport.normalizeIp(requestResolver.resolveClientIp(request));
    }

    private List<String> parseWebRtcIps(String rawWebRtcIps) {
        if (StrUtil.isBlank(rawWebRtcIps)) {
            return List.of();
        }
        return signalSupport.normalizeIpCandidates(Arrays.asList(rawWebRtcIps.split("[,\\s]+")));
    }

    private String normalizeStatus(String rawStatus) {
        return StrUtil.blankToDefault(rawStatus, "").trim().toLowerCase(Locale.ROOT);
    }
}
