package com.example.ShoppingSystem.filter.preauth.domain;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.store.PreAuthBindingRepository;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthIpNormalizer;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class WebRtcIpConsistencyService {

    private static final Logger log = LoggerFactory.getLogger(WebRtcIpConsistencyService.class);

    public static final String STATUS_OK = "ok";
    public static final String ERROR_CODE_MISMATCH = "WEBRTC_IP_MISMATCH";
    public static final String ERROR_MESSAGE_MISMATCH = "网络环境异常，请关闭 VPN/代理后重试";
    public static final String ERROR_CODE_SIGNAL_REQUIRED = "WEBRTC_SIGNAL_REQUIRED";
    public static final String ERROR_MESSAGE_SIGNAL_REQUIRED = "网络环境校验失败，请关闭 VPN/代理后重试";

    private final PreAuthProperties properties;
    private final PreAuthRequestResolver requestResolver;
    private final PreAuthBindingRepository bindingRepository;
    private final TrustedExitIpMatcher trustedExitIpMatcher;

    public WebRtcIpConsistencyService(PreAuthProperties properties,
                                      PreAuthRequestResolver requestResolver,
                                      PreAuthBindingRepository bindingRepository,
                                      TrustedExitIpMatcher trustedExitIpMatcher) {
        this.properties = properties;
        this.requestResolver = requestResolver;
        this.bindingRepository = bindingRepository;
        this.trustedExitIpMatcher = trustedExitIpMatcher;
    }

    public CheckResult checkAndPersist(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            logDecision(request, "ALLOW_DISABLED", "", Signal.missing(), true);
            return CheckResult.allow();
        }

        Signal signal = resolveSignal(request);
        String httpIp = normalizeIp(requestResolver.resolveClientIp(request));
        if (!signal.hasReport()) {
            if (requiresVerifiedWebRtcSignal(request)) {
                logDecision(request, "BLOCK_SIGNAL_MISSING", httpIp, signal, false);
                return CheckResult.blockRequired(httpIp, signal.webRtcIp(), signal.status());
            }
            logDecision(request, "ALLOW_SIGNAL_MISSING", httpIp, signal, true);
            return CheckResult.allow();
        }

        if (!signal.hasPublicIp() && requiresVerifiedWebRtcSignal(request)) {
            persistSignal(request, signal, false);
            logDecision(request, "BLOCK_SIGNAL_UNVERIFIED", httpIp, signal, false);
            return CheckResult.blockRequired(httpIp, signal.webRtcIp(), signal.status());
        }
        boolean strictMatch = signal.hasPublicIp()
                && StrUtil.isNotBlank(httpIp)
                && signal.matchesHttpIp(httpIp);
        boolean trustedExitMatch = signal.hasPublicIp()
                && StrUtil.isNotBlank(httpIp)
                && !strictMatch
                && trustedExitIpMatcher.isTrustedMatch(httpIp, signal.webRtcIps());
        boolean mismatch = signal.hasPublicIp()
                && StrUtil.isNotBlank(httpIp)
                && !strictMatch
                && !trustedExitMatch;

        persistSignal(request, signal, mismatch);
        if (mismatch) {
            logDecision(request, "BLOCK_IP_MISMATCH", httpIp, signal, false);
            return CheckResult.block(httpIp, signal.webRtcIp(), signal.status());
        }
        logDecision(request, trustedExitMatch ? "ALLOW_TRUSTED_EXIT_GROUP" : "ALLOW_MATCH", httpIp, signal, true);
        return CheckResult.allow();
    }

    private void logDecision(HttpServletRequest request,
                             String decision,
                             String httpIp,
                             Signal signal,
                             boolean allowed) {
        if (request == null) {
            return;
        }
        log.info("WebRTC IP consistency: decision={}, allowed={}, method={}, uri={}, httpIp={}, webRtcIp={}, webRtcIps={}, webRtcStatus={}, xForwardedFor={}, xRealIp={}, remoteAddr={}",
                decision,
                allowed,
                request.getMethod(),
                request.getRequestURI(),
                httpIp,
                signal == null ? "" : signal.webRtcIp(),
                signal == null ? "" : signal.joinedWebRtcIps(),
                signal == null ? "" : signal.status(),
                header(request, "X-Forwarded-For"),
                header(request, "X-Real-IP"),
                request.getRemoteAddr());
    }

    private String header(HttpServletRequest request, String name) {
        return request == null ? "" : StrUtil.blankToDefault(request.getHeader(name), "");
    }

    private boolean requiresVerifiedWebRtcSignal(HttpServletRequest request) {
        String uri = request == null ? "" : StrUtil.blankToDefault(request.getRequestURI(), "");
        if (!uri.startsWith("/shopping/admin")) {
            return false;
        }
        String method = request == null ? "" : StrUtil.blankToDefault(request.getMethod(), "");
        if (!"GET".equalsIgnoreCase(method)) {
            return true;
        }
        String accept = StrUtil.blankToDefault(request.getHeader("Accept"), "");
        String requestedWith = StrUtil.blankToDefault(request.getHeader("X-Requested-With"), "");
        return uri.contains("/api/")
                || uri.startsWith("/shopping/admin/session/")
                || accept.contains("application/json")
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith);
    }

    public PreAuthBinding applyRequestState(PreAuthBinding binding, HttpServletRequest request) {
        if (binding == null || !properties.isEnabled()) {
            return binding;
        }

        Signal signal = resolveSignal(request);
        if (!signal.hasReport()) {
            return binding;
        }

        String httpIp = normalizeIp(requestResolver.resolveClientIp(request));
        boolean strictMatch = signal.hasPublicIp()
                && StrUtil.isNotBlank(httpIp)
                && signal.matchesHttpIp(httpIp);
        boolean trustedExitMatch = signal.hasPublicIp()
                && StrUtil.isNotBlank(httpIp)
                && !strictMatch
                && trustedExitIpMatcher.isTrustedMatch(httpIp, signal.webRtcIps());
        boolean mismatch = signal.hasPublicIp()
                && StrUtil.isNotBlank(httpIp)
                && !strictMatch
                && !trustedExitMatch;
        return withSignal(binding, signal, mismatch);
    }

    private void persistSignal(HttpServletRequest request, Signal signal, boolean mismatch) {
        String token = requestResolver.resolveIncomingToken(request);
        if (StrUtil.isBlank(token)) {
            return;
        }

        PreAuthBinding existing = bindingRepository.load(token.trim());
        if (existing == null) {
            return;
        }
        bindingRepository.save(withSignal(existing, signal, mismatch));
    }

    private PreAuthBinding withSignal(PreAuthBinding binding, Signal signal, boolean mismatch) {
        return binding.withWebRtcState(
                signal.webRtcIp(),
                signal.status(),
                System.currentTimeMillis(),
                Math.max(0, binding.webRtcMismatchCount()) + (mismatch ? 1 : 0)
        );
    }

    private Signal resolveSignal(HttpServletRequest request) {
        if (request == null) {
            return Signal.missing();
        }
        String rawIp = StrUtil.blankToDefault(request.getHeader(PreAuthHeaders.HEADER_WEBRTC_IP), "").trim();
        String rawIps = StrUtil.blankToDefault(request.getHeader(PreAuthHeaders.HEADER_WEBRTC_IPS), "").trim();
        String rawStatus = StrUtil.blankToDefault(request.getHeader(PreAuthHeaders.HEADER_WEBRTC_STATUS), "").trim();
        String status = normalizeStatus(rawStatus);
        List<String> webRtcIps = normalizeIpCandidates(rawIp, rawIps);
        String webRtcIp = webRtcIps.isEmpty() ? "" : webRtcIps.get(0);
        return new Signal(webRtcIp, webRtcIps, status);
    }

    private List<String> normalizeIpCandidates(String rawPrimaryIp, String rawCandidateIps) {
        Set<String> normalized = new LinkedHashSet<>();
        addNormalizedIp(normalized, rawPrimaryIp);
        if (StrUtil.isNotBlank(rawCandidateIps)) {
            String[] parts = rawCandidateIps.split("[,\\s]+");
            for (String part : parts) {
                addNormalizedIp(normalized, part);
            }
        }
        return new ArrayList<>(normalized);
    }

    private void addNormalizedIp(Set<String> target, String rawIp) {
        String normalized = normalizeIp(rawIp);
        if (StrUtil.isNotBlank(normalized)) {
            target.add(normalized);
        }
    }

    private String normalizeStatus(String rawStatus) {
        if (StrUtil.isBlank(rawStatus)) {
            return "";
        }
        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case STATUS_OK, "timeout", "unsupported", "private_only", "error" -> normalized;
            default -> "error";
        };
    }

    private String normalizeIp(String rawIp) {
        return PreAuthIpNormalizer.normalizeIp(rawIp);
    }

    private record Signal(String webRtcIp, List<String> webRtcIps, String status) {
        static Signal missing() {
            return new Signal("", List.of(), "");
        }

        boolean hasReport() {
            return !webRtcIps.isEmpty() || StrUtil.isNotBlank(webRtcIp) || StrUtil.isNotBlank(status);
        }

        boolean hasPublicIp() {
            return STATUS_OK.equals(status) && (!webRtcIps.isEmpty() || StrUtil.isNotBlank(webRtcIp));
        }

        boolean matchesHttpIp(String httpIp) {
            String normalizedHttpIp = StrUtil.blankToDefault(httpIp, "").trim().toLowerCase(Locale.ROOT);
            if (StrUtil.isBlank(normalizedHttpIp)) {
                return false;
            }
            if (webRtcIps.contains(normalizedHttpIp)) {
                return true;
            }
            return StrUtil.equals(normalizedHttpIp, webRtcIp);
        }

        String joinedWebRtcIps() {
            if (!webRtcIps.isEmpty()) {
                return String.join(",", webRtcIps);
            }
            return StrUtil.blankToDefault(webRtcIp, "");
        }
    }

    public record CheckResult(boolean allowed,
                              String errorCode,
                              String message,
                              String httpIp,
                              String webRtcIp,
                              String webRtcStatus) {

        static CheckResult allow() {
            return new CheckResult(true, "", "", "", "", "");
        }

        static CheckResult block(String httpIp, String webRtcIp, String webRtcStatus) {
            return new CheckResult(
                    false,
                    ERROR_CODE_MISMATCH,
                    ERROR_MESSAGE_MISMATCH,
                    httpIp,
                    webRtcIp,
                    webRtcStatus
            );
        }

        static CheckResult blockRequired() {
            return blockRequired("", "", "");
        }

        static CheckResult blockRequired(String httpIp, String webRtcIp, String webRtcStatus) {
            return new CheckResult(
                    false,
                    ERROR_CODE_SIGNAL_REQUIRED,
                    ERROR_MESSAGE_SIGNAL_REQUIRED,
                    httpIp,
                    webRtcIp,
                    webRtcStatus
            );
        }
    }
}
