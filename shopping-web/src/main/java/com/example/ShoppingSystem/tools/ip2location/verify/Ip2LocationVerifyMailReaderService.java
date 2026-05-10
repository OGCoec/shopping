package com.example.ShoppingSystem.tools.ip2location.verify;

import com.example.ShoppingSystem.common.proxy.LocalProxyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.ShoppingSystem.tools.ip2location.verify.imap.ImapFolderScanPlanner;
import com.example.ShoppingSystem.tools.ip2location.verify.imap.Ip2LocationVerifyMailImapScanner;
import com.example.ShoppingSystem.tools.ip2location.verify.matcher.Ip2LocationMailMatcher;
import com.example.ShoppingSystem.tools.ip2location.verify.matcher.Ip2LocationVerifyLinkExtractor;
import com.example.ShoppingSystem.tools.ip2location.verify.model.MailCredentials;
import com.example.ShoppingSystem.tools.ip2location.verify.oauth.MicrosoftImapAccessTokenClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates Microsoft OAuth and IMAP scanning for IP2Location verification mails.
 */
@Service
public class Ip2LocationVerifyMailReaderService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    private final MicrosoftImapAccessTokenClient tokenClient;
    private final List<Ip2LocationVerifyMailImapScanner> imapScanners;
    private final Ip2LocationMailMatcher mailMatcher;

    @Autowired
    public Ip2LocationVerifyMailReaderService(
            ObjectMapper objectMapper,
            @Value("${ip2location.verify-mail.token-url:https://login.microsoftonline.com/common/oauth2/v2.0/token}") String tokenUrl,
            @Value("${ip2location.verify-mail.imap-host:imap-mail.outlook.com}") String imapHost,
            @Value("${ip2location.verify-mail.imap-port:993}") int imapPort,
            @Value("${ip2location.verify-mail.imap-scope:https://outlook.office.com/IMAP.AccessAsUser.All offline_access}") String imapScope,
            @Value("${ip2location.verify-mail.fetch-count:20}") int fetchCount,
            @Value("${ip2location.verify-mail.folder-order:Junk Email,INBOX}") String folderOrder,
            @Value("${ip2location.verify-mail.sender-domain-filter:ip2location.io}") String senderDomainFilter,
            @Value("${ip2location.verify-mail.subject-keyword-filter:ip2location}") String subjectKeywordFilter,
            @Value("${ip2location.verify-mail.socks-host:127.0.0.1}") String socksHost,
            @Value("${ip2location.verify-mail.socks-port:7892}") int socksPort,
            @Value("${ip2location.verify-mail.max-age-minutes:0}") int maxAgeMinutes,
            @Value("${local-proxy.ports:7892,7897}") String candidateProxyPorts,
            LocalProxyResolver localProxyResolver) {
        this(
                objectMapper,
                HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(),
                tokenUrl,
                imapHost,
                imapPort,
                imapScope,
                fetchCount,
                parseFolderOrder(folderOrder),
                senderDomainFilter,
                subjectKeywordFilter,
                resolveProxyTargets(localProxyResolver, socksHost, socksPort, candidateProxyPorts),
                maxAgeMinutes
        );
    }

    private Ip2LocationVerifyMailReaderService(ObjectMapper objectMapper,
                                               HttpClient httpClient,
                                               String tokenUrl,
                                               String imapHost,
                                               int imapPort,
                                               String imapScope,
                                               int fetchCount,
                                               List<String> folderOrder,
                                               String senderDomainFilter,
                                               String subjectKeywordFilter,
                                               List<ProxyTarget> proxyTargets,
                                               int maxAgeMinutes) {
        this.mailMatcher = new Ip2LocationMailMatcher(senderDomainFilter, subjectKeywordFilter);
        this.tokenClient = new MicrosoftImapAccessTokenClient(
                objectMapper,
                httpClient,
                tokenUrl,
                imapScope,
                REQUEST_TIMEOUT
        );
        this.imapScanners = proxyTargets.stream()
                .map(proxyTarget -> new Ip2LocationVerifyMailImapScanner(
                        imapHost,
                        imapPort,
                        fetchCount,
                        proxyTarget.host(),
                        proxyTarget.port(),
                        maxAgeMinutes,
                        REQUEST_TIMEOUT,
                        new ImapFolderScanPlanner(folderOrder),
                        mailMatcher,
                        new Ip2LocationVerifyLinkExtractor()
                ))
                .toList();
    }

    Ip2LocationVerifyMailReaderService(ObjectMapper objectMapper,
                                       HttpClient httpClient,
                                       String tokenUrl,
                                       String imapHost,
                                       int imapPort,
                                       String imapScope,
                                       int fetchCount,
                                       List<String> folderOrder,
                                       String senderDomainFilter,
                                       String subjectKeywordFilter,
                                       String socksHost,
                                       int socksPort,
                                       int maxAgeMinutes) {
        this(
                objectMapper,
                httpClient,
                tokenUrl,
                imapHost,
                imapPort,
                imapScope,
                fetchCount,
                folderOrder,
                senderDomainFilter,
                subjectKeywordFilter,
                List.of(new ProxyTarget(socksHost, socksPort)),
                maxAgeMinutes
        );
    }

    public VerifyLinkReadResult readLatestVerifyLinkFromCredentials(String credentials) {
        MailCredentials parsed = MailCredentials.parse(credentials);
        return readLatestVerifyLink(parsed.email(), parsed.clientId(), parsed.refreshToken());
    }

    public VerifyLinkReadResult readLatestVerifyLink(String email, String clientId, String refreshToken) {
        CredentialValidation validation = validateCredentials(email, clientId, refreshToken);
        if (!validation.valid()) {
            return VerifyLinkReadResult.failed(validation.reason());
        }

        MicrosoftImapAccessTokenClient.AccessTokenResult tokenResult =
                tokenClient.refresh(clientId.trim(), refreshToken.trim());
        if (!tokenResult.success()) {
            return VerifyLinkReadResult.failed(tokenResult.reason());
        }
        return scanVerifyLinkWithProxyFallback(email.trim(), tokenResult.accessToken());
    }

    public RegistrationMailCheckResult checkRegistrationMailTraceFromCredentials(String credentials) {
        MailCredentials parsed = MailCredentials.parse(credentials);
        return checkRegistrationMailTrace(parsed.email(), parsed.clientId(), parsed.refreshToken());
    }

    public RegistrationMailCheckResult checkRegistrationMailTrace(String email, String clientId, String refreshToken) {
        CredentialValidation validation = validateCredentials(email, clientId, refreshToken);
        if (!validation.valid()) {
            return RegistrationMailCheckResult.failed(validation.reason());
        }
        if (!mailMatcher.hasSenderFilter()) {
            return RegistrationMailCheckResult.failed("sender_domain_filter_missing");
        }

        MicrosoftImapAccessTokenClient.AccessTokenResult tokenResult =
                tokenClient.refresh(clientId.trim(), refreshToken.trim());
        if (!tokenResult.success()) {
            return RegistrationMailCheckResult.failed(tokenResult.reason());
        }
        return scanSenderTraceWithProxyFallback(email.trim(), tokenResult.accessToken());
    }

    private VerifyLinkReadResult scanVerifyLinkWithProxyFallback(String email, String accessToken) {
        VerifyLinkReadResult lastResult = VerifyLinkReadResult.failed("imap_scanner_missing");
        for (Ip2LocationVerifyMailImapScanner scanner : imapScanners) {
            VerifyLinkReadResult result = scanner.scanVerifyLink(email, accessToken);
            lastResult = result;
            if (result.success() || !isProxyRetryable(result.reason())) {
                return result;
            }
        }
        return lastResult;
    }

    private RegistrationMailCheckResult scanSenderTraceWithProxyFallback(String email, String accessToken) {
        RegistrationMailCheckResult lastResult = RegistrationMailCheckResult.failed("imap_scanner_missing");
        for (Ip2LocationVerifyMailImapScanner scanner : imapScanners) {
            RegistrationMailCheckResult result = scanner.scanSenderTrace(email, accessToken);
            lastResult = result;
            if (result.success() || !isProxyRetryable(result.reason())) {
                return result;
            }
        }
        return lastResult;
    }

    private boolean isProxyRetryable(String reason) {
        return "imap_error".equals(reason);
    }

    private CredentialValidation validateCredentials(String email, String clientId, String refreshToken) {
        if (isBlank(email)) {
            return CredentialValidation.invalid("invalid_email");
        }
        if (isBlank(clientId)) {
            return CredentialValidation.invalid("invalid_client_id");
        }
        if (isBlank(refreshToken)) {
            return CredentialValidation.invalid("invalid_refresh_token");
        }
        return CredentialValidation.ok();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> parseFolderOrder(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("Junk Email", "INBOX");
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            ordered.add(part.trim());
        }
        if (ordered.isEmpty()) {
            return List.of("Junk Email", "INBOX");
        }
        return new ArrayList<>(ordered);
    }

    private static List<ProxyTarget> resolveProxyTargets(LocalProxyResolver localProxyResolver,
                                                         String configuredHost,
                                                         int configuredPort,
                                                         String candidateProxyPorts) {
        LocalProxyResolver.ProxySelection proxySelection =
                localProxyResolver.resolveOrConfigured(configuredHost, configuredPort);
        List<ProxyTarget> targets = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        InetSocketAddress proxyAddress = proxySelection.address();
        if (proxyAddress != null) {
            addProxyTarget(targets, seen, proxyAddress.getHostString(), proxyAddress.getPort());
        }
        addProxyTarget(targets, seen, configuredHost, configuredPort);
        if (candidateProxyPorts != null) {
            for (String rawPort : candidateProxyPorts.split(",")) {
                addProxyTarget(targets, seen, configuredHost, parseProxyPort(rawPort));
            }
        }
        if (targets.isEmpty()) {
            targets.add(new ProxyTarget("", -1));
        }
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

    private record ProxyTarget(String host, int port) {
    }

    private record CredentialValidation(boolean valid, String reason) {
        private static CredentialValidation ok() {
            return new CredentialValidation(true, "ok");
        }

        private static CredentialValidation invalid(String reason) {
            return new CredentialValidation(false, reason);
        }
    }

    public record RegistrationMailCheckResult(boolean success,
                                              String reason,
                                              String email,
                                              String folderName,
                                              String sender,
                                              String subject,
                                              String receivedAt) {
        public static RegistrationMailCheckResult succeeded(String email,
                                                            String folderName,
                                                            String sender,
                                                            String subject,
                                                            String receivedAt) {
            return new RegistrationMailCheckResult(
                    true,
                    "ok",
                    email,
                    folderName,
                    sender,
                    subject,
                    receivedAt
            );
        }

        public static RegistrationMailCheckResult failed(String reason) {
            return new RegistrationMailCheckResult(
                    false,
                    reason,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    public record VerifyLinkReadResult(boolean success,
                                       String reason,
                                       String email,
                                       String folderName,
                                       String sender,
                                       String subject,
                                       String receivedAt,
                                       String verifyUrl,
                                       String verifyToken) {
        public static VerifyLinkReadResult succeeded(String email,
                                                     String folderName,
                                                     String sender,
                                                     String subject,
                                                     String receivedAt,
                                                     String verifyUrl,
                                                     String verifyToken) {
            return new VerifyLinkReadResult(
                    true,
                    "ok",
                    email,
                    folderName,
                    sender,
                    subject,
                    receivedAt,
                    verifyUrl,
                    verifyToken
            );
        }

        public static VerifyLinkReadResult failed(String reason) {
            return new VerifyLinkReadResult(
                    false,
                    reason,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
