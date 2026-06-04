package com.example.ShoppingSystem.tools.openai.mail;

import com.example.ShoppingSystem.tools.ip2location.verify.imap.ImapFolderScanPlanner;
import com.example.ShoppingSystem.tools.openai.mail.OpenAiMailStatusReaderService.MailStatusScanResult;
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

public final class OpenAiMailImapScanner {

    private static final Logger log = LoggerFactory.getLogger(OpenAiMailImapScanner.class);

    private final String imapHost;
    private final int imapPort;
    private final int fetchCount;
    private final int maxCandidateMessages;
    private final String socksHost;
    private final int socksPort;
    private final Duration requestTimeout;
    private final ImapFolderScanPlanner folderScanPlanner;
    private final OpenAiMailMatcher mailMatcher;
    private final OpenAiMailBodyExtractor bodyExtractor;

    public OpenAiMailImapScanner(String imapHost,
                                 int imapPort,
                                 int fetchCount,
                                 int maxCandidateMessages,
                                 String socksHost,
                                 int socksPort,
                                 Duration requestTimeout,
                                 ImapFolderScanPlanner folderScanPlanner,
                                 OpenAiMailMatcher mailMatcher,
                                 OpenAiMailBodyExtractor bodyExtractor) {
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.fetchCount = Math.max(1, fetchCount);
        this.maxCandidateMessages = Math.max(1, maxCandidateMessages);
        this.socksHost = socksHost == null ? "" : socksHost.trim();
        this.socksPort = socksPort;
        this.requestTimeout = requestTimeout;
        this.folderScanPlanner = folderScanPlanner;
        this.mailMatcher = mailMatcher;
        this.bodyExtractor = bodyExtractor;
    }

    public MailStatusScanResult scanStatus(String email, String accessToken, Duration scanTimeout) {
        return scanStatusInternal(email, accessToken, scanTimeout).withImapRoute(routeLabel());
    }

    private MailStatusScanResult scanStatusInternal(String email, String accessToken, Duration scanTimeout) {
        long deadlineNanos = System.nanoTime() + Math.max(1L, scanTimeout.toNanos());
        Store store = null;
        try {
            store = connect(email, accessToken);
            List<String> scanOrder = folderScanPlanner.resolveScanOrder(store);
            CandidateSnapshot firstCandidate = null;
            for (String folderName : scanOrder) {
                if (deadlineExceeded(deadlineNanos)) {
                    return MailStatusScanResult.timeout();
                }
                FolderScanOutcome outcome = scanFolder(store, email, folderName, deadlineNanos);
                if (outcome.result() != null) {
                    return outcome.result();
                }
                if (firstCandidate == null && outcome.firstCandidate() != null) {
                    firstCandidate = outcome.firstCandidate();
                }
            }
            if (firstCandidate == null) {
                return MailStatusScanResult.notRegistered();
            }
            return MailStatusScanResult.registeredNormal(
                    firstCandidate.folderName(),
                    firstCandidate.sender(),
                    firstCandidate.subject(),
                    firstCandidate.receivedAt()
            );
        } catch (AuthenticationFailedException e) {
            log.warn("OpenAI mail IMAP XOAUTH2 authentication failed, email={}, reason={}", email, e.getMessage());
            return MailStatusScanResult.imapAuthFailed();
        } catch (MessagingException e) {
            log.warn("OpenAI mail IMAP read failed, email={}, reason={}", email, e.getMessage());
            return MailStatusScanResult.imapError("imap_error");
        } finally {
            closeQuietly(store);
        }
    }

    private Store connect(String email, String accessToken) throws MessagingException {
        Session session = Session.getInstance(buildImapProperties());
        Store store = session.getStore("imaps");
        log.info("Connecting IMAP via XOAUTH2 for OpenAI mail status check, host={}, port={}, route={}, email={}",
                imapHost, imapPort, routeLabel(), email);
        store.connect(imapHost, imapPort, email, accessToken);
        return store;
    }

    private FolderScanOutcome scanFolder(Store store,
                                         String email,
                                         String folderName,
                                         long deadlineNanos) {
        Folder folder = null;
        try {
            folder = openReadableFolder(store, folderName);
            if (folder == null || folder.getMessageCount() <= 0) {
                return FolderScanOutcome.empty();
            }

            Message[] messages = findMessages(folder, mailMatcher.buildCandidateTerm());
            if (messages.length == 0) {
                return FolderScanOutcome.empty();
            }

            CandidateSnapshot firstCandidate = null;
            int candidateCount = 0;
            for (int i = messages.length - 1; i >= 0; i -= 1) {
                if (deadlineExceeded(deadlineNanos)) {
                    return FolderScanOutcome.result(MailStatusScanResult.timeout());
                }
                Message message = messages[i];
                String subject = trimToNull(message.getSubject());
                String sender = firstFromAddress(message);
                if (!mailMatcher.isPotentialOpenAiMail(sender, subject)) {
                    continue;
                }

                candidateCount += 1;
                Date sentDate = message.getSentDate();
                CandidateSnapshot snapshot = new CandidateSnapshot(
                        folderName,
                        sender,
                        subject,
                        sentDate == null ? null : sentDate.toInstant().toString()
                );
                if (firstCandidate == null) {
                    firstCandidate = snapshot;
                }

                String evidencePhrase = mailMatcher.findEvidencePhrase(subject + "\n" + bodyExtractor.extractText(message));
                if (evidencePhrase != null) {
                    return FolderScanOutcome.result(MailStatusScanResult.detectedEvidenceFound(
                            snapshot.folderName(),
                            snapshot.sender(),
                            snapshot.subject(),
                            snapshot.receivedAt(),
                            evidencePhrase
                    ));
                }
                if (candidateCount >= maxCandidateMessages) {
                    break;
                }
            }
            return new FolderScanOutcome(null, firstCandidate);
        } catch (MessagingException | IOException e) {
            log.warn("OpenAI mail IMAP folder scan failed, email={}, folder={}, reason={}",
                    email, folderName, e.getMessage());
            return FolderScanOutcome.result(MailStatusScanResult.imapError("folder_read_error_" + sanitizeReason(folderName)));
        } finally {
            closeQuietly(folder);
        }
    }

    private Message[] findMessages(Folder folder, SearchTerm searchTerm) throws MessagingException {
        int totalMessages = folder.getMessageCount();
        Message[] messages = new Message[0];
        if (searchTerm != null) {
            try {
                messages = folder.search(searchTerm);
            } catch (MessagingException e) {
                log.warn("OpenAI mail IMAP candidate search failed, folder={}, reason={}",
                        folder.getFullName(), e.getMessage());
            }
        }
        if (messages == null || messages.length == 0) {
            int startIndex = Math.max(1, totalMessages - fetchCount + 1);
            messages = folder.getMessages(startIndex, totalMessages);
        } else if (messages.length > maxCandidateMessages) {
            int keep = Math.max(1, maxCandidateMessages);
            Message[] limited = new Message[keep];
            System.arraycopy(messages, messages.length - keep, limited, 0, keep);
            messages = limited;
        }

        FetchProfile fetchProfile = new FetchProfile();
        fetchProfile.add(FetchProfile.Item.ENVELOPE);
        folder.fetch(messages, fetchProfile);
        return messages;
    }

    private Folder openReadableFolder(Store store, String folderName) throws MessagingException {
        Folder folder = store.getFolder(folderName);
        if (folder == null || !folder.exists()) {
            return null;
        }
        folder.open(Folder.READ_ONLY);
        return folder;
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
        if (!isBlank(socksHost) && socksPort > 0) {
            props.put("mail.imaps.socks.host", socksHost);
            props.put("mail.imaps.socks.port", String.valueOf(socksPort));
        }
        return props;
    }

    public String routeLabel() {
        if (isBlank(socksHost) || socksPort <= 0) {
            return "DIRECT";
        }
        return "SOCKS " + socksHost + ":" + socksPort;
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

    private boolean deadlineExceeded(long deadlineNanos) {
        return System.nanoTime() >= deadlineNanos;
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

    private record CandidateSnapshot(String folderName,
                                     String sender,
                                     String subject,
                                     String receivedAt) {
    }

    private record FolderScanOutcome(MailStatusScanResult result,
                                     CandidateSnapshot firstCandidate) {
        private static FolderScanOutcome empty() {
            return new FolderScanOutcome(null, null);
        }

        private static FolderScanOutcome result(MailStatusScanResult result) {
            return new FolderScanOutcome(result, null);
        }
    }
}
