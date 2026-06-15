package com.example.ShoppingSystem.security.risk.webrtc;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.domain.TrustedExitIpMatcher;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthIpNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class WebRtcRiskSignalSupport {

    private final TrustedExitIpMatcher trustedExitIpMatcher;

    public WebRtcRiskSignalSupport(TrustedExitIpMatcher trustedExitIpMatcher) {
        this.trustedExitIpMatcher = trustedExitIpMatcher;
    }

    public WebRtcRiskDecision evaluate(String rawHttpIp,
                                       List<String> rawWebRtcIps,
                                       String rawWebRtcStatus) {
        String httpIp = normalizeIp(rawHttpIp);
        List<String> webRtcIps = normalizeIpCandidates(rawWebRtcIps);
        String webRtcStatus = normalizeWebRtcStatus(rawWebRtcStatus);
        if (!"ok".equals(webRtcStatus) || webRtcIps.isEmpty()) {
            return new WebRtcRiskDecision(
                    WebRtcRiskStatus.CHALLENGE_REQUIRED,
                    "WEBRTC_SIGNAL_UNVERIFIED",
                    httpIp,
                    webRtcIps,
                    webRtcStatus,
                    false
            );
        }

        boolean strictMatch = StrUtil.isNotBlank(httpIp) && webRtcIps.contains(httpIp);
        boolean trustedMatch = StrUtil.isNotBlank(httpIp)
                && !strictMatch
                && trustedExitIpMatcher.isTrustedMatch(httpIp, webRtcIps);
        boolean mismatch = StrUtil.isNotBlank(httpIp) && !strictMatch && !trustedMatch;
        if (mismatch) {
            return new WebRtcRiskDecision(
                    WebRtcRiskStatus.BLOCKED,
                    "WEBRTC_IP_MISMATCH",
                    httpIp,
                    webRtcIps,
                    webRtcStatus,
                    true
            );
        }
        return new WebRtcRiskDecision(
                WebRtcRiskStatus.NORMAL,
                "WEBRTC_IP_MATCH",
                httpIp,
                webRtcIps,
                webRtcStatus,
                false
        );
    }

    public List<String> normalizeIpCandidates(List<String> rawCandidateIps) {
        Set<String> normalized = new LinkedHashSet<>();
        if (rawCandidateIps != null) {
            for (String rawIp : rawCandidateIps) {
                addNormalizedIp(normalized, rawIp);
            }
        }
        return new ArrayList<>(normalized);
    }

    public String normalizeWebRtcStatus(String rawStatus) {
        if (StrUtil.isBlank(rawStatus)) {
            return "error";
        }
        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ok", "timeout", "unsupported", "private_only", "error" -> normalized;
            default -> "error";
        };
    }

    public String normalizeIp(String rawIp) {
        return PreAuthIpNormalizer.normalizeIp(rawIp);
    }

    private void addNormalizedIp(Set<String> target, String rawIp) {
        String normalized = normalizeIp(rawIp);
        if (StrUtil.isNotBlank(normalized)) {
            target.add(normalized);
        }
    }
}
