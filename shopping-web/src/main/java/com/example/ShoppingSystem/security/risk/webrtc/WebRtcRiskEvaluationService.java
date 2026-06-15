package com.example.ShoppingSystem.security.risk.webrtc;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.store.PreAuthBindingRepository;
import com.example.ShoppingSystem.security.risk.AccountNetworkRiskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WebRtcRiskEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(WebRtcRiskEvaluationService.class);

    private final WebRtcRiskSignalSupport signalSupport;
    private final WebRtcRiskStateStore stateStore;
    private final PreAuthBindingRepository preAuthBindingRepository;
    private final AccountNetworkRiskService accountNetworkRiskService;
    private final AdminSessionService adminSessionService;

    public WebRtcRiskEvaluationService(WebRtcRiskSignalSupport signalSupport,
                                       WebRtcRiskStateStore stateStore,
                                       PreAuthBindingRepository preAuthBindingRepository,
                                       AccountNetworkRiskService accountNetworkRiskService,
                                       AdminSessionService adminSessionService) {
        this.signalSupport = signalSupport;
        this.stateStore = stateStore;
        this.preAuthBindingRepository = preAuthBindingRepository;
        this.accountNetworkRiskService = accountNetworkRiskService;
        this.adminSessionService = adminSessionService;
    }

    public void evaluateAndWriteBack(WebRtcRiskMessage message) {
        if (message == null || message.getScope() == null || StrUtil.isBlank(message.getSubjectRef())) {
            return;
        }
        WebRtcRiskDecision decision = signalSupport.evaluate(
                message.getHttpIp(),
                message.getWebRtcIps(),
                message.getWebRtcStatus()
        );
        long seenAt = Math.max(0L, message.getObservedAtEpochMillis());
        if (shouldKeepExistingNormalState(message, decision)) {
            log.info("WebRTC risk unverified report ignored because existing NORMAL state is reusable, scope={}, httpIp={}, webRtcStatus={}",
                    message.getScope(),
                    decision.httpIp(),
                    decision.webRtcStatus());
            return;
        }
        stateStore.save(message.getScope(), message.getSubjectRef(), decision, seenAt);
        switch (message.getScope()) {
            case PREAUTH -> writePreAuthState(message.getSubjectRef(), decision, seenAt);
            case USER -> writeUserState(message, decision, seenAt);
            case ADMIN -> writeAdminState(message.getSubjectRef(), decision, seenAt);
        }
    }

    private boolean shouldKeepExistingNormalState(WebRtcRiskMessage message, WebRtcRiskDecision decision) {
        if (message == null || decision == null || decision.status() != WebRtcRiskStatus.CHALLENGE_REQUIRED) {
            return false;
        }
        if (decision.webRtcIps() != null && !decision.webRtcIps().isEmpty()) {
            return false;
        }
        if ("ok".equalsIgnoreCase(StrUtil.blankToDefault(decision.webRtcStatus(), ""))) {
            return false;
        }
        String currentHttpIp = signalSupport.normalizeIp(decision.httpIp());
        if (StrUtil.isBlank(currentHttpIp)) {
            return false;
        }
        WebRtcRiskState existing = stateStore.load(message.getScope(), message.getSubjectRef());
        if (existing == null || existing.status() != WebRtcRiskStatus.NORMAL) {
            return false;
        }
        if (!"ok".equalsIgnoreCase(StrUtil.blankToDefault(existing.webRtcStatus(), ""))) {
            return false;
        }
        if (StrUtil.isBlank(existing.webRtcIps())) {
            return false;
        }
        String existingHttpIp = signalSupport.normalizeIp(existing.httpIp());
        return StrUtil.equals(existingHttpIp, currentHttpIp);
    }

    private void writePreAuthState(String token, WebRtcRiskDecision decision, long seenAt) {
        PreAuthBinding existing = preAuthBindingRepository.load(token);
        if (existing == null) {
            return;
        }
        int mismatchCount = Math.max(0, existing.webRtcMismatchCount()) + (decision.mismatch() ? 1 : 0);
        PreAuthBinding updated = decision.status() == WebRtcRiskStatus.BLOCKED
                ? existing.withRiskAndWebRtcState(
                0,
                "L6",
                String.join(",", decision.webRtcIps()),
                decision.webRtcStatus(),
                seenAt,
                mismatchCount
        )
                : existing.withWebRtcState(
                String.join(",", decision.webRtcIps()),
                decision.webRtcStatus(),
                seenAt,
                mismatchCount
        );
        preAuthBindingRepository.save(updated);
    }

    private void writeUserState(WebRtcRiskMessage message, WebRtcRiskDecision decision, long seenAt) {
        Long userId = parseLong(message.getSubjectRef());
        if (userId == null) {
            return;
        }
        accountNetworkRiskService.recordAsyncWebRtcRisk(
                userId,
                decision.httpIp(),
                message.getDeviceFingerprintHash(),
                decision,
                seenAt
        );
    }

    private void writeAdminState(String sessionToken, WebRtcRiskDecision decision, long seenAt) {
        adminSessionService.updateWebRtcRisk(
                sessionToken,
                String.join(",", decision.webRtcIps()),
                decision.webRtcStatus(),
                seenAt,
                decision.mismatch() ? 1 : 0,
                decision.status().name()
        );
    }

    private Long parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
