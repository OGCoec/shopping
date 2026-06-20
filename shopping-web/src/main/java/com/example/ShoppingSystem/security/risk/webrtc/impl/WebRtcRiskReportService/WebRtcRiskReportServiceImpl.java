package com.example.ShoppingSystem.security.risk.webrtc.impl.WebRtcRiskReportService;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthHashingService;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import com.example.ShoppingSystem.security.token.AuthTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskReportService;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskDispatcher;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskIdempotencyService;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskMessage;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskProperties;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskReportRequest;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskReportResponse;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskScope;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskSignalSupport;
@Service
public class WebRtcRiskReportServiceImpl implements WebRtcRiskReportService {

    private static final Logger log = LoggerFactory.getLogger(WebRtcRiskReportService.class);

    private final WebRtcRiskProperties properties;
    private final WebRtcRiskSignalSupport signalSupport;
    private final WebRtcRiskDispatcher dispatcher;
    private final WebRtcRiskIdempotencyService idempotencyService;
    private final PreAuthRequestResolver requestResolver;
    private final PreAuthHashingService hashingService;
    private final AuthTokenService authTokenService;
    private final AdminSessionService adminSessionService;

    public WebRtcRiskReportServiceImpl(WebRtcRiskProperties properties,
                                   WebRtcRiskSignalSupport signalSupport,
                                   WebRtcRiskDispatcher dispatcher,
                                   WebRtcRiskIdempotencyService idempotencyService,
                                   PreAuthRequestResolver requestResolver,
                                   PreAuthHashingService hashingService,
                                   AuthTokenService authTokenService,
                                   AdminSessionService adminSessionService) {
        this.properties = properties;
        this.signalSupport = signalSupport;
        this.dispatcher = dispatcher;
        this.idempotencyService = idempotencyService;
        this.requestResolver = requestResolver;
        this.hashingService = hashingService;
        this.authTokenService = authTokenService;
        this.adminSessionService = adminSessionService;
    }

    public WebRtcRiskReportResponse reportPreAuthOrUser(HttpServletRequest request, WebRtcRiskReportRequest report) {
        if (!properties.isEnabled()) {
            return WebRtcRiskReportResponse.skipped("disabled");
        }
        boolean queued = false;
        String preAuthToken = requestResolver.resolveIncomingToken(request);
        if (StrUtil.isNotBlank(preAuthToken)) {
            queued = publish(WebRtcRiskScope.PREAUTH, preAuthToken.trim(), request, report) || queued;
        }
        Long userId = authTokenService.tryResolveUserIdFromAccessToken(request);
        if (userId != null) {
            queued = publish(WebRtcRiskScope.USER, String.valueOf(userId), request, report) || queued;
        }
        return queued ? WebRtcRiskReportResponse.accepted() : WebRtcRiskReportResponse.skipped("no_subject_or_duplicate");
    }

    public WebRtcRiskReportResponse reportAdmin(HttpServletRequest request, WebRtcRiskReportRequest report) {
        if (!properties.isEnabled()) {
            return WebRtcRiskReportResponse.skipped("disabled");
        }
        String sessionToken = adminSessionService.resolveSessionToken(request);
        if (StrUtil.isBlank(sessionToken)) {
            return WebRtcRiskReportResponse.skipped("no_admin_session");
        }
        boolean queued = publish(WebRtcRiskScope.ADMIN, sessionToken, request, report);
        return queued ? WebRtcRiskReportResponse.accepted() : WebRtcRiskReportResponse.skipped("duplicate");
    }

    private boolean publish(WebRtcRiskScope scope,
                            String subjectRef,
                            HttpServletRequest request,
                            WebRtcRiskReportRequest report) {
        WebRtcRiskReportRequest safeReport = report == null
                ? new WebRtcRiskReportRequest(List.of(), "error", null, "")
                : report;
        String httpIp = signalSupport.normalizeIp(requestResolver.resolveClientIp(request));
        List<String> webRtcIps = signalSupport.normalizeIpCandidates(safeReport.webRtcIps());
        String webRtcStatus = signalSupport.normalizeWebRtcStatus(safeReport.webRtcStatus());
        if (!idempotencyService.markReport(scope, subjectRef, httpIp, webRtcStatus, webRtcIps)) {
            return false;
        }
        WebRtcRiskMessage message = WebRtcRiskMessage.builder()
                .eventId(scope.name().toLowerCase() + "-" + IdUtil.fastSimpleUUID())
                .scope(scope)
                .subjectRef(subjectRef)
                .httpIp(httpIp)
                .webRtcIps(webRtcIps)
                .webRtcStatus(webRtcStatus)
                .deviceFingerprintHash(resolveDeviceFingerprintHash(request))
                .userAgentHash(hashingService.sha256(requestResolver.resolveUserAgent(request)))
                .durationMillis(safeReport.durationMillis())
                .diagnosticReason(StrUtil.blankToDefault(safeReport.diagnosticReason(), ""))
                .observedAtEpochMillis(System.currentTimeMillis())
                .build();
        try {
            dispatcher.dispatch(message);
            return true;
        } catch (Exception e) {
            log.warn("WebRTC risk report publish failed, scope={}, subjectRef={}, httpIp={}, webRtcStatus={}, error={}",
                    scope, subjectRef, httpIp, webRtcStatus, e.getMessage());
            return false;
        }
    }

    private String resolveDeviceFingerprintHash(HttpServletRequest request) {
        String rawFingerprint = request == null ? "" : request.getHeader(PreAuthHeaders.HEADER_DEVICE_FINGERPRINT);
        String normalized = requestResolver.normalizeFingerprint(rawFingerprint, request);
        return hashingService.sha256(normalized);
    }
}
