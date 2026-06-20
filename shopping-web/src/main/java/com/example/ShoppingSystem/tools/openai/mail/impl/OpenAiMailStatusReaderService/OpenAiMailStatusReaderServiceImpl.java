package com.example.ShoppingSystem.tools.openai.mail.impl.OpenAiMailStatusReaderService;

import com.example.ShoppingSystem.common.proxy.LocalProxyResolver;
import com.example.ShoppingSystem.tools.ip2location.verify.imap.ImapFolderScanPlanner;
import com.example.ShoppingSystem.tools.ip2location.verify.oauth.MicrosoftImapAccessTokenClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.ShoppingSystem.tools.openai.mail.OpenAiMailStatusReaderService;
import com.example.ShoppingSystem.tools.openai.mail.OpenAiMailBodyExtractor;
import com.example.ShoppingSystem.tools.openai.mail.OpenAiMailImapScanner;
import com.example.ShoppingSystem.tools.openai.mail.OpenAiMailMatcher;
@Service
public class OpenAiMailStatusReaderServiceImpl implements OpenAiMailStatusReaderService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiMailStatusReaderService.class);

    public static final String STATUS_NOT_REGISTERED = "NOT_REGISTERED";
    public static final String STATUS_REGISTERED_NORMAL = "REGISTERED_NORMAL";
    public static final String STATUS_DETECTED_EVIDENCE_FOUND = "DETECTED_EVIDENCE_FOUND";
    public static final String STATUS_MICROSOFT_ACCOUNT_ABUSE = "MICROSOFT_ACCOUNT_ABUSE";
    public static final String STATUS_TOKEN_REFRESH_FAILED = "TOKEN_REFRESH_FAILED";
    public static final String STATUS_IMAP_AUTH_FAILED = "IMAP_AUTH_FAILED";
    public static final String STATUS_IMAP_ERROR = "IMAP_ERROR";
    public static final String STATUS_MAIL_SCAN_TIMEOUT = "MAIL_SCAN_TIMEOUT";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    private final MicrosoftImapAccessTokenClient tokenClient;
    private final List<OpenAiMailImapScanner> imapScanners;

    @Autowired
    public OpenAiMailStatusReaderServiceImpl(
            ObjectMapper objectMapper,
            @Value("${openai.status-mail.token-url:https://login.microsoftonline.com/common/oauth2/v2.0/token}") String tokenUrl,
            @Value("${openai.status-mail.imap-host:imap-mail.outlook.com}") String imapHost,
            @Value("${openai.status-mail.imap-port:993}") int imapPort,
            @Value("${openai.status-mail.imap-scope:https://outlook.office.com/IMAP.AccessAsUser.All offline_access}") String imapScope,
            @Value("${openai.status-mail.fetch-count:80}") int fetchCount,
            @Value("${openai.status-mail.max-candidate-messages:30}") int maxCandidateMessages,
            @Value("${openai.status-mail.folder-order:Junk Email,INBOX}") String folderOrder,
            @Value("${openai.status-mail.sender-keywords:openai,chatgpt}") String senderKeywords,
            @Value("${openai.status-mail.subject-keywords:OpenAI,ChatGPT}") String subjectKeywords,
            @Value("${openai.status-mail.detected-phrases:Your account has been detected,deactivated,deactivating}") String detectedPhrases,
            @Value("${openai.status-mail.socks-host:127.0.0.1}") String socksHost,
            @Value("${openai.status-mail.socks-port:7892}") int socksPort,
            @Value("${local-proxy.ports:7892,7897}") String candidateProxyPorts,
            @Value("${openai.status-mail.route-mode:auto}") String routeMode,
            @Value("${openai.status-mail.route-probe-timeout-ms:1500}") int routeProbeTimeoutMs,
            LocalProxyResolver localProxyResolver) {
        this.tokenClient = new MicrosoftImapAccessTokenClient(
                objectMapper,
                HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(),
                tokenUrl,
                imapScope,
                REQUEST_TIMEOUT
        );
        OpenAiMailMatcher matcher = new OpenAiMailMatcher(senderKeywords, subjectKeywords, detectedPhrases);
        OpenAiMailBodyExtractor bodyExtractor = new OpenAiMailBodyExtractor();
        List<String> folders = parseFolderOrder(folderOrder);
        this.imapScanners = resolveProxyTargets(
                localProxyResolver,
                socksHost,
                socksPort,
                candidateProxyPorts,
                imapHost,
                imapPort,
                routeMode,
                routeProbeTimeoutMs
        ).stream()
                .map(proxyTarget -> new OpenAiMailImapScanner(
                        imapHost,
                        imapPort,
                        fetchCount,
                        maxCandidateMessages,
                        proxyTarget.host(),
                        proxyTarget.port(),
                        REQUEST_TIMEOUT,
                        new ImapFolderScanPlanner(folders),
                        matcher,
                        bodyExtractor
                ))
                .toList();
    }

    public MailStatusScanResult checkStatus(String email,
                                            String clientId,
                                            String refreshToken,
                                            Duration scanTimeout) {
        if (isBlank(email) || isBlank(clientId) || isBlank(refreshToken)) {
            return MailStatusScanResult.imapError("invalid_reader_input");
        }
        MicrosoftImapAccessTokenClient.AccessTokenResult tokenResult =
                tokenClient.refresh(clientId.trim(), refreshToken.trim());
        if (!tokenResult.success()) {
            if (MicrosoftImapAccessTokenClient.REASON_MICROSOFT_ACCOUNT_ABUSE.equals(tokenResult.reason())) {
                return MailStatusScanResult.microsoftAccountAbuse();
            }
            return MailStatusScanResult.tokenRefreshFailed(tokenResult.reason());
        }
        long deadlineNanos = System.nanoTime() + Math.max(1L, scanTimeout.toNanos());
        MailStatusScanResult lastResult = MailStatusScanResult.imapError("imap_scanner_missing");
        for (OpenAiMailImapScanner scanner : imapScanners) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                return MailStatusScanResult.timeout();
            }
            MailStatusScanResult result = scanner.scanStatus(
                    email.trim(),
                    tokenResult.accessToken(),
                    Duration.ofNanos(remainingNanos)
            );
            lastResult = result;
            if (!STATUS_IMAP_ERROR.equals(result.status())) {
                return result;
            }
        }
        return lastResult;
    }

    private static List<String> parseFolderOrder(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("Junk Email", "INBOX");
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            if (part != null && !part.isBlank()) {
                ordered.add(part.trim());
            }
        }
        if (ordered.isEmpty()) {
            return List.of("Junk Email", "INBOX");
        }
        return new ArrayList<>(ordered);
    }

    private static List<ProxyTarget> resolveProxyTargets(LocalProxyResolver localProxyResolver,
                                                         String configuredHost,
                                                         int configuredPort,
                                                         String candidateProxyPorts,
                                                         String imapHost,
                                                         int imapPort,
                                                         String routeMode,
                                                         int routeProbeTimeoutMs) {
        String normalizedRouteMode = normalizeRouteMode(routeMode);
        LocalProxyResolver.ProxySelection proxySelection =
                localProxyResolver.resolveOrConfigured(configuredHost, configuredPort);
        List<ProxyTarget> targets = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if ("direct".equals(normalizedRouteMode) || "auto".equals(normalizedRouteMode)) {
            addDirectTarget(targets, seen);
        }
        InetSocketAddress proxyAddress = proxySelection.address();
        if (!"direct".equals(normalizedRouteMode)) {
            if (proxyAddress != null) {
                addProxyTarget(targets, seen, proxyAddress.getHostString(), proxyAddress.getPort());
            }
            addProxyTarget(targets, seen, configuredHost, configuredPort);
            if (candidateProxyPorts != null) {
                for (String rawPort : candidateProxyPorts.split(",")) {
                    addProxyTarget(targets, seen, configuredHost, parseProxyPort(rawPort));
                }
            }
        }
        if ("auto".equals(normalizedRouteMode)) {
            return orderByRouteProbe(targets, imapHost, imapPort, routeProbeTimeoutMs);
        }
        log.info("OpenAI mail IMAP route mode selected, mode={}, routes={}",
                normalizedRouteMode, targets.stream().map(ProxyTarget::label).toList());
        return targets;
    }

    private static void addProxyTarget(List<ProxyTarget> targets,
                                       Set<String> seen,
                                       String host,
                                       int port) {
        if (host == null || host.isBlank() || port <= 0 || port > 65535) {
            return;
        }
        String normalizedHost = host.trim();
        String key = normalizedHost + ":" + port;
        if (seen.add(key)) {
            targets.add(new ProxyTarget(normalizedHost, port));
        }
    }

    private static void addDirectTarget(List<ProxyTarget> targets, Set<String> seen) {
        if (seen.add("DIRECT")) {
            targets.add(new ProxyTarget("", -1));
        }
    }

    private static List<ProxyTarget> orderByRouteProbe(List<ProxyTarget> targets,
                                                       String imapHost,
                                                       int imapPort,
                                                       int routeProbeTimeoutMs) {
        if (targets.isEmpty()) {
            return targets;
        }
        int timeoutMs = Math.max(300, routeProbeTimeoutMs);
        List<RouteProbeResult> probeResults = new ArrayList<>();
        for (int index = 0; index < targets.size(); index += 1) {
            RouteProbeResult result = probeRoute(index, targets.get(index), imapHost, imapPort, timeoutMs);
            probeResults.add(result);
            log.info("OpenAI mail IMAP route probe result, route={}, reachable={}, elapsedMillis={}, reason={}",
                    result.target().label(), result.reachable(), result.elapsedMillis(), result.reason());
        }
        List<ProxyTarget> orderedTargets = probeResults.stream()
                .sorted(routeProbeComparator())
                .map(RouteProbeResult::target)
                .toList();
        log.info("OpenAI mail IMAP route order selected, routes={}",
                orderedTargets.stream().map(ProxyTarget::label).toList());
        return orderedTargets;
    }

    private static Comparator<RouteProbeResult> routeProbeComparator() {
        return (left, right) -> {
            int reachableCompare = Boolean.compare(right.reachable(), left.reachable());
            if (reachableCompare != 0) {
                return reachableCompare;
            }
            if (left.reachable() && right.reachable()) {
                int elapsedCompare = Long.compare(left.elapsedMillis(), right.elapsedMillis());
                if (elapsedCompare != 0) {
                    return elapsedCompare;
                }
            }
            return Integer.compare(left.index(), right.index());
        };
    }

    private static RouteProbeResult probeRoute(int index,
                                               ProxyTarget target,
                                               String imapHost,
                                               int imapPort,
                                               int timeoutMs) {
        long startedNanos = System.nanoTime();
        try (Socket socket = createProbeSocket(target)) {
            socket.connect(new InetSocketAddress(imapHost, imapPort), timeoutMs);
            return new RouteProbeResult(index, target, true, elapsedMillis(startedNanos), "ok");
        } catch (IOException | RuntimeException e) {
            return new RouteProbeResult(index, target, false, elapsedMillis(startedNanos),
                    e.getClass().getSimpleName());
        }
    }

    private static Socket createProbeSocket(ProxyTarget target) {
        if (target.direct()) {
            return new Socket();
        }
        Proxy proxy = new Proxy(
                Proxy.Type.SOCKS,
                new InetSocketAddress(target.host(), target.port())
        );
        return new Socket(proxy);
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    private static String normalizeRouteMode(String routeMode) {
        String normalized = routeMode == null ? "" : routeMode.trim().toLowerCase(Locale.ROOT);
        if ("direct".equals(normalized) || "proxy".equals(normalized) || "auto".equals(normalized)) {
            return normalized;
        }
        return "auto";
    }

    private static int parseProxyPort(String rawPort) {
        if (rawPort == null || rawPort.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(rawPort.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ProxyTarget(String host, int port) {
        private boolean direct() {
            return host == null || host.isBlank() || port <= 0;
        }

        private String label() {
            if (direct()) {
                return "DIRECT";
            }
            return "SOCKS " + host + ":" + port;
        }
    }

    private record RouteProbeResult(int index,
                                    ProxyTarget target,
                                    boolean reachable,
                                    long elapsedMillis,
                                    String reason) {
    }
}
