package com.example.ShoppingSystem.tools.ip2location.verify.imap;

import com.example.ShoppingSystem.tools.ip2location.verify.Ip2LocationVerifyMailReaderService;
import com.example.ShoppingSystem.tools.ip2location.verify.matcher.Ip2LocationMailMatcher;
import com.example.ShoppingSystem.tools.ip2location.verify.matcher.Ip2LocationVerifyLinkExtractor;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.SearchTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class Ip2LocationVerifyMailImapScanner {

    private static final Logger log = LoggerFactory.getLogger(Ip2LocationVerifyMailImapScanner.class);

    private final String imapHost;
    private final int imapPort;
    private final int fetchCount;
    private final String socksHost;
    private final int socksPort;
    private final int maxAgeMinutes;
    private final Duration requestTimeout;
    private final ImapFolderScanPlanner folderScanPlanner;
    private final Ip2LocationMailMatcher mailMatcher;
    private final Ip2LocationVerifyLinkExtractor verifyLinkExtractor;

    public Ip2LocationVerifyMailImapScanner(String imapHost,
                                            int imapPort,
                                            int fetchCount,
                                            String socksHost,
                                            int socksPort,
                                            int maxAgeMinutes,
                                            Duration requestTimeout,
                                            ImapFolderScanPlanner folderScanPlanner,
                                            Ip2LocationMailMatcher mailMatcher,
                                            Ip2LocationVerifyLinkExtractor verifyLinkExtractor) {
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.fetchCount = Math.max(1, fetchCount);
        this.socksHost = socksHost == null ? "" : socksHost.trim();
        this.socksPort = socksPort;
        this.maxAgeMinutes = Math.max(0, maxAgeMinutes);
        this.requestTimeout = requestTimeout;
        this.folderScanPlanner = folderScanPlanner;
        this.mailMatcher = mailMatcher;
        this.verifyLinkExtractor = verifyLinkExtractor;
    }

    public Ip2LocationVerifyMailReaderService.VerifyLinkReadResult scanVerifyLink(String email, String accessToken) {
        Store store = null;
        try {
            store = connect(email, accessToken, "verify link");
            List<String> scanOrder = folderScanPlanner.resolveScanOrder(store);
            log.info("Resolved IMAP scan order, folders={}", scanOrder);
            for (String folderName : scanOrder) {
                Ip2LocationVerifyMailReaderService.VerifyLinkReadResult result = scanVerifyLinkFolder(store, email, folderName);
                if (result.success()) {
                    return result;
                }
            }
            return Ip2LocationVerifyMailReaderService.VerifyLinkReadResult.failed("verify_link_not_found");
        } catch (AuthenticationFailedException e) {
            log.warn("IMAP XOAUTH2 authentication failed, email={}, reason={}", email, e.getMessage());
            return Ip2LocationVerifyMailReaderService.VerifyLinkReadResult.failed("imap_auth_failed");
        } catch (MessagingException e) {
            log.warn("IMAP read failed, email={}, reason={}", email, e.getMessage());
            return Ip2LocationVerifyMailReaderService.VerifyLinkReadResult.failed("imap_error");
        } finally {
            closeQuietly(store);
        }
    }

    public Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult scanSenderTrace(String email, String accessToken) {
        Store store = null;
        try {
            store = connect(email, accessToken, "sender-only trace check");
            List<String> scanOrder = folderScanPlanner.resolveScanOrder(store);
            log.info("Resolved IMAP sender-only scan order, folders={}", scanOrder);
            for (String folderName : scanOrder) {
                Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult result =
                        scanSenderTraceFolder(store, email, folderName);
                if (result.success()) {
                    return result;
                }
            }
            return Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult.failed("ip2location_sender_not_found");
        } catch (AuthenticationFailedException e) {
            log.warn("IMAP XOAUTH2 authentication failed, email={}, reason={}", email, e.getMessage());
            return Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult.failed("imap_auth_failed");
        } catch (MessagingException e) {
            log.warn("IMAP sender-only read failed, email={}, reason={}", email, e.getMessage());
            return Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult.failed("imap_error");
        } finally {
            closeQuietly(store);
        }
    }

    private Store connect(String email, String accessToken, String purpose) throws MessagingException {
        Session session = Session.getInstance(buildImapProperties());
        Store store = session.getStore("imaps");
        log.info("Connecting IMAP via XOAUTH2 for {}, host={}, port={}, email={}",
                purpose, imapHost, imapPort, email);
        store.connect(imapHost, imapPort, email, accessToken);
        log.info("IMAP connected for {}, email={}", purpose, email);
        return store;
    }

    private Ip2LocationVerifyMailReaderService.VerifyLinkReadResult scanVerifyLinkFolder(
            Store store,
            String email,
            String folderName) {
        Folder folder = null;
        try {
            folder = openReadableFolder(store, folderName);
            if (folder == null) {
                return Ip2LocationVerifyMailReaderService.VerifyLinkReadResult.failed(
                        "folder_not_found_" + sanitizeReason(folderName));
            }

            int totalMessages = folder.getMessageCount();
            if (totalMessages <= 0) {
                return Ip2LocationVerifyMailReaderService.VerifyLinkReadResult.failed(
                        "folder_empty_" + sanitizeReason(folderName));
            }

            Message[] messages = findMessages(folder, totalMessages, mailMatcher.buildVerifyLinkCandidateTerm(), "candidate");
            if (messages.length == 0) {
                return Ip2LocationVerifyMailReaderService.VerifyLinkReadResult.failed(
                        "verify_link_not_found_in_" + sanitizeReason(folderName));
            }
            long cutoffEpochMillis = resolveCutoffEpochMillis();

            for (int i = messages.length - 1; i >= 0; i--) {
                Message message = messages[i];
                Date sentDate = message.getSentDate();
                String subject = trimToNull(message.getSubject());
                String sender = firstFromAddress(message);

                if (isOlderThanCutoff(sentDate, cutoffEpochMillis)
                        || !mailMatcher.isPotentialIp2LocationMail(sender, subject)) {
                    continue;
                }

                String verifyUrl = verifyLinkExtractor.extractVerifyUrlFromMessage(message);
                String verifyToken = verifyLinkExtractor.extractVerifyToken(verifyUrl);
                if (isBlank(verifyToken)) {
                    continue;
                }

                log.info("Found IP2Location verify link, folder={}, from={}, subject={}, sentDate={}",
                        folderName, sender, subject, sentDate);
                return Ip2LocationVerifyMailReaderService.VerifyLinkReadResult.succeeded(
                        email,
                        folderName,
                        sender,
                        subject,
                        sentDate == null ? null : sentDate.toInstant().toString(),
                        verifyUrl,
                        verifyToken
                );
            }
            return Ip2LocationVerifyMailReaderService.VerifyLinkReadResult.failed(
                    "verify_link_not_found_in_" + sanitizeReason(folderName));
        } catch (MessagingException | IOException e) {
            log.warn("Scan IMAP folder failed, folder={}, reason={}", folderName, e.getMessage());
            return Ip2LocationVerifyMailReaderService.VerifyLinkReadResult.failed(
                    "folder_read_error_" + sanitizeReason(folderName));
        } finally {
            closeQuietly(folder);
        }
    }

    private Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult scanSenderTraceFolder(
            Store store,
            String email,
            String folderName) {
        Folder folder = null;
        try {
            folder = openReadableFolder(store, folderName);
            if (folder == null) {
                return Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult.failed(
                        "folder_not_found_" + sanitizeReason(folderName));
            }

            int totalMessages = folder.getMessageCount();
            if (totalMessages <= 0) {
                return Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult.failed(
                        "folder_empty_" + sanitizeReason(folderName));
            }

            Message[] messages = findMessages(folder, totalMessages, mailMatcher.buildSenderOnlyTerm(), "sender-only");
            if (messages.length == 0) {
                return Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult.failed(
                        "ip2location_sender_not_found_in_" + sanitizeReason(folderName));
            }
            long cutoffEpochMillis = resolveCutoffEpochMillis();

            for (int i = messages.length - 1; i >= 0; i--) {
                Message message = messages[i];
                Date sentDate = message.getSentDate();
                String subject = trimToNull(message.getSubject());
                String sender = firstFromAddress(message);

                if (isOlderThanCutoff(sentDate, cutoffEpochMillis) || !mailMatcher.senderMatches(sender)) {
                    continue;
                }

                log.info("Found IP2Location sender trace, folder={}, from={}, subject={}, sentDate={}",
                        folderName, sender, subject, sentDate);
                return Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult.succeeded(
                        email,
                        folderName,
                        sender,
                        subject,
                        sentDate == null ? null : sentDate.toInstant().toString()
                );
            }
            return Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult.failed(
                    "ip2location_sender_not_found_in_" + sanitizeReason(folderName));
        } catch (MessagingException e) {
            log.warn("Scan IMAP sender-only folder failed, folder={}, reason={}", folderName, e.getMessage());
            return Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult.failed(
                    "folder_read_error_" + sanitizeReason(folderName));
        } finally {
            closeQuietly(folder);
        }
    }

    private Folder openReadableFolder(Store store, String folderName) throws MessagingException {
        Folder folder = store.getFolder(folderName);
        if (folder == null || !folder.exists()) {
            return null;
        }
        folder.open(Folder.READ_ONLY);
        return folder;
    }

    private Message[] findMessages(Folder folder,
                                   int totalMessages,
                                   SearchTerm searchTerm,
                                   String logLabel) throws MessagingException {
        Message[] messages = new Message[0];
        if (searchTerm != null) {
            try {
                messages = folder.search(searchTerm);
                if (messages != null && messages.length > 0) {
                    log.info("IMAP {} search matched {} messages in folder={}",
                            logLabel, messages.length, folder.getFullName());
                }
            } catch (MessagingException e) {
                log.warn("IMAP {} search failed, folder={}, reason={}",
                        logLabel, folder.getFullName(), e.getMessage());
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

    private Properties buildImapProperties() {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", String.valueOf(imapPort));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", String.valueOf(requestTimeout.toMillis()));
        props.put("mail.imaps.timeout", String.valueOf(requestTimeout.toMillis()));
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

    private boolean isOlderThanCutoff(Date sentDate, long cutoffEpochMillis) {
        return cutoffEpochMillis > 0L && sentDate != null && sentDate.getTime() < cutoffEpochMillis;
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

    private String sanitizeReason(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return "unknown";
        }
        return folderName.trim().replace(' ', '_').toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
