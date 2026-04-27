package com.example.ShoppingSystem.tools.ip2location.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.BodyPart;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the latest IP2Location verify link from Microsoft mail via IMAP XOAUTH2.
 */
@Service
public class Ip2LocationVerifyMailReaderService {

    private static final Logger log = LoggerFactory.getLogger(Ip2LocationVerifyMailReaderService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final Pattern VERIFY_URL_PATTERN = Pattern.compile(
            "https://www\\.ip2location\\.io/verify\\?code=([A-Za-z0-9_-]+)",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> JUNK_FOLDER_KEYWORDS = List.of(
            "junk", "spam", "bulk", "垃圾", "垃圾邮件", "广告邮件");
    private static final List<String> INBOX_FOLDER_KEYWORDS = List.of(
            "inbox", "收件箱");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String tokenUrl;
    private final String imapHost;
    private final int imapPort;
    private final String imapScope;
    private final int fetchCount;
    private final List<String> folderOrder;
    private final String senderDomainFilter;
    private final String subjectKeywordFilter;
    private final String socksHost;
    private final int socksPort;
    private final int maxAgeMinutes;

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
            @Value("${ip2location.verify-mail.max-age-minutes:0}") int maxAgeMinutes) {
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
                socksHost,
                socksPort,
                maxAgeMinutes
        );
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
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.tokenUrl = tokenUrl;
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.imapScope = imapScope;
        this.fetchCount = Math.max(1, fetchCount);
        this.folderOrder = folderOrder == null || folderOrder.isEmpty()
                ? List.of("Junk Email", "INBOX")
                : List.copyOf(folderOrder);
        this.senderDomainFilter = normalizeFilter(senderDomainFilter);
        this.subjectKeywordFilter = normalizeFilter(subjectKeywordFilter);
        this.socksHost = socksHost == null ? "" : socksHost.trim();
        this.socksPort = socksPort;
        this.maxAgeMinutes = Math.max(0, maxAgeMinutes);
    }

    public VerifyLinkReadResult readLatestVerifyLinkFromCredentials(String credentials) {
        Credentials parsed = parseCredentials(credentials);
        return readLatestVerifyLink(parsed.email(), parsed.clientId(), parsed.refreshToken());
    }

    public VerifyLinkReadResult readLatestVerifyLink(String email, String clientId, String refreshToken) {
        if (isBlank(email)) {
            return VerifyLinkReadResult.failed("invalid_email");
        }
        if (isBlank(clientId)) {
            return VerifyLinkReadResult.failed("invalid_client_id");
        }
        if (isBlank(refreshToken)) {
            return VerifyLinkReadResult.failed("invalid_refresh_token");
        }

        AccessTokenResult tokenResult = refreshImapAccessToken(clientId.trim(), refreshToken.trim());
        if (!tokenResult.success()) {
            return VerifyLinkReadResult.failed(tokenResult.reason());
        }
        return scanFoldersViaImap(email.trim(), tokenResult.accessToken());
    }

    private AccessTokenResult refreshImapAccessToken(String clientId, String refreshToken) {
        String body = "client_id=" + urlEncode(clientId)
                + "&grant_type=refresh_token"
                + "&refresh_token=" + urlEncode(refreshToken)
                + "&scope=" + urlEncode(imapScope);
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            JsonNode payload = readJson(response.body());
            if (statusCode < 200 || statusCode >= 300) {
                String error = payload == null ? null : text(payload, "error");
                String errorDescription = payload == null ? null : text(payload, "error_description");
                log.warn("IMAP token refresh failed, httpStatus={}, error={}, errorDescription={}",
                        statusCode, error, errorDescription);
                return AccessTokenResult.failed("token_http_status_" + statusCode);
            }

            String accessToken = payload == null ? null : text(payload, "access_token");
            if (isBlank(accessToken)) {
                return AccessTokenResult.failed("access_token_missing");
            }
            log.info("IMAP XOAUTH2 access token acquired, tokenLength={}", accessToken.length());
            return AccessTokenResult.succeeded(accessToken);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("IMAP token refresh exception, reason={}", e.getMessage());
            return AccessTokenResult.failed("token_http_error");
        }
    }

    private VerifyLinkReadResult scanFoldersViaImap(String email, String accessToken) {
        Store store = null;
        try {
            Session session = Session.getInstance(buildImapProperties());
            store = session.getStore("imaps");
            log.info("Connecting IMAP via XOAUTH2, host={}, port={}, email={}", imapHost, imapPort, email);
            store.connect(imapHost, imapPort, email, accessToken);
            log.info("IMAP connected, email={}", email);

            List<String> scanOrder = resolveFolderScanOrder(store);
            log.info("Resolved IMAP scan order, folders={}", scanOrder);
            for (String folderName : scanOrder) {
                VerifyLinkReadResult result = scanFolder(store, email, folderName);
                if (result.success()) {
                    return result;
                }
            }
            return VerifyLinkReadResult.failed("verify_link_not_found");
        } catch (AuthenticationFailedException e) {
            log.warn("IMAP XOAUTH2 authentication failed, email={}, reason={}", email, e.getMessage());
            return VerifyLinkReadResult.failed("imap_auth_failed");
        } catch (MessagingException e) {
            log.warn("IMAP read failed, email={}, reason={}", email, e.getMessage());
            return VerifyLinkReadResult.failed("imap_error");
        } finally {
            closeQuietly(store);
        }
    }

    private List<String> resolveFolderScanOrder(Store store) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        List<String> discoveredFolders = discoverReadableFolders(store);
        for (String folderName : discoveredFolders) {
            if (looksLikeJunkFolder(folderName)) {
                ordered.add(folderName);
            }
        }
        for (String folderName : discoveredFolders) {
            if (looksLikeInboxFolder(folderName)) {
                ordered.add(folderName);
            }
        }

        if (folderOrder != null) {
            for (String configuredName : folderOrder) {
                String resolved = resolveExistingFolderName(discoveredFolders, configuredName);
                if (!isBlank(resolved)) {
                    ordered.add(resolved);
                }
            }
        }

        ordered.addAll(discoveredFolders);
        return new ArrayList<>(ordered);
    }

    private List<String> discoverReadableFolders(Store store) {
        LinkedHashSet<String> folderNames = new LinkedHashSet<>();
        try {
            Folder defaultFolder = store.getDefaultFolder();
            if (defaultFolder == null) {
                return List.of();
            }
            collectReadableFolders(defaultFolder, folderNames, 0);
            log.info("Discovered IMAP folders, folders={}", folderNames);
        } catch (MessagingException e) {
            log.warn("Discover IMAP folders failed, reason={}", e.getMessage());
        }
        return new ArrayList<>(folderNames);
    }

    private void collectReadableFolders(Folder folder, Set<String> collector, int depth) throws MessagingException {
        if (folder == null || depth > 8) {
            return;
        }

        String fullName = trimToNull(folder.getFullName());
        if (fullName != null
                && !fullName.equals("/")
                && !fullName.equalsIgnoreCase("[Gmail]")
                && holdsMessages(folder)) {
            collector.add(fullName);
        }

        Folder[] children = folder.list();
        if (children == null || children.length == 0) {
            return;
        }
        for (Folder child : children) {
            collectReadableFolders(child, collector, depth + 1);
        }
    }

    private boolean holdsMessages(Folder folder) throws MessagingException {
        int type = folder.getType();
        return (type & Folder.HOLDS_MESSAGES) != 0;
    }

    private VerifyLinkReadResult scanFolder(Store store, String email, String folderName) {
        Folder folder = null;
        try {
            folder = store.getFolder(folderName);
            if (folder == null || !folder.exists()) {
                return VerifyLinkReadResult.failed("folder_not_found_" + sanitizeReason(folderName));
            }

            folder.open(Folder.READ_ONLY);
            int totalMessages = folder.getMessageCount();
            if (totalMessages <= 0) {
                return VerifyLinkReadResult.failed("folder_empty_" + sanitizeReason(folderName));
            }

            Message[] messages = findCandidateMessages(folder, totalMessages);
            if (messages.length == 0) {
                return VerifyLinkReadResult.failed("verify_link_not_found_in_" + sanitizeReason(folderName));
            }
            long cutoffEpochMillis = resolveCutoffEpochMillis();

            for (int i = messages.length - 1; i >= 0; i--) {
                Message message = messages[i];
                Date sentDate = message.getSentDate();
                String subject = trimToNull(message.getSubject());
                String sender = firstFromAddress(message);

                if (cutoffEpochMillis > 0L && sentDate != null && sentDate.getTime() < cutoffEpochMillis) {
                    continue;
                }

                if (!isPotentialIp2LocationMail(sender, subject)) {
                    continue;
                }

                String verifyUrl = extractVerifyUrlFromMessage(message);
                if (verifyUrl == null) {
                    continue;
                }
                if (!looksLikeIp2LocationMail(sender, subject, verifyUrl)) {
                    continue;
                }

                String verifyToken = extractVerifyToken(verifyUrl);
                if (isBlank(verifyToken)) {
                    continue;
                }

                log.info("Found IP2Location verify link, folder={}, from={}, subject={}, sentDate={}",
                        folderName, sender, subject, sentDate);
                return VerifyLinkReadResult.succeeded(
                        email,
                        folderName,
                        sender,
                        subject,
                        sentDate == null ? null : sentDate.toInstant().toString(),
                        verifyUrl,
                        verifyToken
                );
            }
            return VerifyLinkReadResult.failed("verify_link_not_found_in_" + sanitizeReason(folderName));
        } catch (MessagingException | IOException e) {
            log.warn("Scan IMAP folder failed, folder={}, reason={}", folderName, e.getMessage());
            return VerifyLinkReadResult.failed("folder_read_error_" + sanitizeReason(folderName));
        } finally {
            closeQuietly(folder);
        }
    }

    private Message[] findCandidateMessages(Folder folder, int totalMessages) throws MessagingException {
        SearchTerm candidateSearchTerm = buildCandidateSearchTerm();
        Message[] messages = new Message[0];

        if (candidateSearchTerm != null) {
            try {
                messages = folder.search(candidateSearchTerm);
                if (messages != null && messages.length > 0) {
                    log.info("IMAP candidate search matched {} messages in folder={}", messages.length, folder.getFullName());
                }
            } catch (MessagingException e) {
                log.warn("IMAP candidate search failed, folder={}, reason={}", folder.getFullName(), e.getMessage());
            }
        }

        if (messages == null || messages.length == 0) {
            int startIndex = Math.max(1, totalMessages - fetchCount + 1);
            messages = folder.getMessages(startIndex, totalMessages);
        } else if (messages.length > fetchCount) {
            int keep = Math.max(1, fetchCount);
            Message[] limited = new Message[keep];
            System.arraycopy(messages, messages.length - keep, limited, 0, keep);
            messages = limited;
        }

        FetchProfile fetchProfile = new FetchProfile();
        fetchProfile.add(FetchProfile.Item.ENVELOPE);
        folder.fetch(messages, fetchProfile);
        return messages;
    }

    private SearchTerm buildCandidateSearchTerm() {
        List<SearchTerm> searchTerms = new ArrayList<>(2);
        if (!isBlank(senderDomainFilter)) {
            searchTerms.add(new FromStringTerm(senderDomainFilter));
        }
        if (!isBlank(subjectKeywordFilter)) {
            searchTerms.add(new SubjectTerm(subjectKeywordFilter));
        }
        if (searchTerms.isEmpty()) {
            return null;
        }
        if (searchTerms.size() == 1) {
            return searchTerms.get(0);
        }
        return new OrTerm(searchTerms.get(0), searchTerms.get(1));
    }

    private Properties buildImapProperties() {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", String.valueOf(imapPort));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", String.valueOf(REQUEST_TIMEOUT.toMillis()));
        props.put("mail.imaps.timeout", String.valueOf(REQUEST_TIMEOUT.toMillis()));
        props.put("mail.imaps.auth.mechanisms", "XOAUTH2");
        props.put("mail.imaps.auth.login.disable", "true");
        props.put("mail.imaps.auth.plain.disable", "true");
        if (!isBlank(socksHost)) {
            props.put("mail.imaps.socks.host", socksHost);
            props.put("mail.imaps.socks.port", String.valueOf(socksPort));
            log.info("Using SOCKS5 proxy for IMAP, host={}, port={}", socksHost, socksPort);
        }
        return props;
    }

    private long resolveCutoffEpochMillis() {
        if (maxAgeMinutes <= 0) {
            return -1L;
        }
        return System.currentTimeMillis() - Duration.ofMinutes(maxAgeMinutes).toMillis();
    }

    private String extractVerifyUrlFromMessage(Message message) throws MessagingException, IOException {
        String fromSubject = extractVerifyUrl(message.getSubject());
        if (fromSubject != null) {
            return fromSubject;
        }
        return extractVerifyUrlFromPart(message);
    }

    private String extractVerifyUrlFromPart(Part part) throws MessagingException, IOException {
        if (part == null) {
            return null;
        }
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            if (content instanceof String text) {
                return extractVerifyUrl(text);
            }
            return null;
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            if (content instanceof String html) {
                return extractVerifyUrl(html);
            }
            return null;
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof MimeMultipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart bodyPart = multipart.getBodyPart(i);
                    String found = extractVerifyUrlFromPart(bodyPart);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part nestedPart) {
                return extractVerifyUrlFromPart(nestedPart);
            }
        }
        return null;
    }

    private boolean looksLikeIp2LocationMail(String sender, String subject, String verifyUrl) {
        if (!isBlank(verifyUrl)) {
            return true;
        }
        return isPotentialIp2LocationMail(sender, subject);
    }

    private boolean isPotentialIp2LocationMail(String sender, String subject) {
        String normalizedSender = sender == null ? "" : sender.toLowerCase(Locale.ROOT);
        String normalizedSubject = subject == null ? "" : subject.toLowerCase(Locale.ROOT);
        if (!isBlank(senderDomainFilter) && normalizedSender.contains(senderDomainFilter)) {
            return true;
        }
        return !isBlank(subjectKeywordFilter) && normalizedSubject.contains(subjectKeywordFilter);
    }

    private String extractVerifyUrl(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        Matcher matcher = VERIFY_URL_PATTERN.matcher(normalizeRawContent(raw));
        if (!matcher.find()) {
            return null;
        }
        return "https://www.ip2location.io/verify?code=" + matcher.group(1);
    }

    private String extractVerifyToken(String verifyUrl) {
        if (isBlank(verifyUrl)) {
            return null;
        }
        Matcher matcher = VERIFY_URL_PATTERN.matcher(verifyUrl);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private Credentials parseCredentials(String credentials) {
        if (isBlank(credentials)) {
            throw new IllegalArgumentException("Credentials string cannot be empty");
        }
        String[] parts = credentials.split("----");
        if (parts.length == 3) {
            return new Credentials(parts[0].trim(), parts[1].trim(), parts[2].trim());
        }
        if (parts.length >= 4) {
            return new Credentials(parts[0].trim(), parts[2].trim(), parts[3].trim());
        }
        throw new IllegalArgumentException("Credentials format must be email----client_id----refresh_token or email----password----client_id----refresh_token");
    }

    private JsonNode readJson(String raw) throws IOException {
        if (isBlank(raw)) {
            return null;
        }
        return objectMapper.readTree(raw);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return null;
        }
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || valueNode.isMissingNode()) {
            return null;
        }
        return valueNode.asText();
    }

    private String firstFromAddress(Message message) throws MessagingException {
        if (message == null) {
            return null;
        }
        jakarta.mail.Address[] from = message.getFrom();
        if (from == null || from.length == 0 || from[0] == null) {
            return null;
        }
        if (from[0] instanceof InternetAddress internetAddress) {
            String address = trimToNull(internetAddress.getAddress());
            if (address != null) {
                return address;
            }
        }
        return trimToNull(from[0].toString());
    }

    private String normalizeRawContent(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replace("\\/", "/")
                .replace("&amp;", "&")
                .replace("&#x2F;", "/")
                .replace("&#47;", "/")
                .replace("&#x3D;", "=")
                .replace("&#61;", "=")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private void closeQuietly(Folder folder) {
        try {
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
        } catch (Exception ignored) {
        }
    }

    private void closeQuietly(Store store) {
        try {
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (Exception ignored) {
        }
    }

    private String urlEncode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private boolean looksLikeJunkFolder(String folderName) {
        return containsFolderKeyword(folderName, JUNK_FOLDER_KEYWORDS);
    }

    private boolean looksLikeInboxFolder(String folderName) {
        return containsFolderKeyword(folderName, INBOX_FOLDER_KEYWORDS);
    }

    private boolean containsFolderKeyword(String folderName, List<String> keywords) {
        if (isBlank(folderName) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String normalized = folderName.trim().toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (!isBlank(keyword) && normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String resolveExistingFolderName(List<String> discoveredFolders, String configuredName) {
        if (discoveredFolders == null || discoveredFolders.isEmpty() || isBlank(configuredName)) {
            return null;
        }
        String normalizedConfigured = configuredName.trim().toLowerCase(Locale.ROOT);
        for (String discoveredFolder : discoveredFolders) {
            if (!isBlank(discoveredFolder)
                    && discoveredFolder.trim().toLowerCase(Locale.ROOT).equals(normalizedConfigured)) {
                return discoveredFolder;
            }
        }
        return null;
    }

    private String sanitizeReason(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return "unknown";
        }
        return folderName.trim().replace(' ', '_').toLowerCase(Locale.ROOT);
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

    private record Credentials(String email, String clientId, String refreshToken) {
    }

    private record AccessTokenResult(boolean success, String reason, String accessToken) {
        private static AccessTokenResult succeeded(String accessToken) {
            return new AccessTokenResult(true, "ok", accessToken);
        }

        private static AccessTokenResult failed(String reason) {
            return new AccessTokenResult(false, reason, null);
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
