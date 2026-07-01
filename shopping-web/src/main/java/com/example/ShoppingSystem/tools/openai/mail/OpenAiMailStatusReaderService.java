package com.example.ShoppingSystem.tools.openai.mail;
import com.example.ShoppingSystem.tools.ip2location.verify.oauth.MicrosoftImapAccessTokenClient;
import java.time.Duration;
public interface OpenAiMailStatusReaderService {
    public static final String STATUS_NOT_REGISTERED = "NOT_REGISTERED";

    public static final String STATUS_REGISTERED_NORMAL = "REGISTERED_NORMAL";

    public static final String STATUS_DETECTED_EVIDENCE_FOUND = "DETECTED_EVIDENCE_FOUND";

    public static final String STATUS_MICROSOFT_ACCOUNT_ABUSE = "MICROSOFT_ACCOUNT_ABUSE";

    public static final String STATUS_TOKEN_REFRESH_FAILED = "TOKEN_REFRESH_FAILED";

    public static final String STATUS_IMAP_AUTH_FAILED = "IMAP_AUTH_FAILED";

    public static final String STATUS_IMAP_ERROR = "IMAP_ERROR";

    public static final String STATUS_MAIL_SCAN_TIMEOUT = "MAIL_SCAN_TIMEOUT";

    public record MailStatusScanResult(String status,
                                           String reason,
                                           boolean openaiMailFound,
                                           String folderName,
                                           String sender,
                                           String subject,
                                           String receivedAt,
                                           String evidencePhrase,
                                           String imapRoute) {
            public static MailStatusScanResult notRegistered() {
                return new MailStatusScanResult(
                        STATUS_NOT_REGISTERED,
                        "openai_or_chatgpt_sender_not_found",
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
            }

            public static MailStatusScanResult registeredNormal(String folderName,
                                                                String sender,
                                                                String subject,
                                                                String receivedAt) {
                return new MailStatusScanResult(
                        STATUS_REGISTERED_NORMAL,
                        "openai_mail_found_no_detected_evidence",
                        true,
                        folderName,
                        sender,
                        subject,
                        receivedAt,
                        null,
                        null
                );
            }

            public static MailStatusScanResult detectedEvidenceFound(String folderName,
                                                                     String sender,
                                                                     String subject,
                                                                     String receivedAt,
                                                                     String evidencePhrase) {
                return new MailStatusScanResult(
                        STATUS_DETECTED_EVIDENCE_FOUND,
                        "detected_phrase_found",
                        true,
                        folderName,
                        sender,
                        subject,
                        receivedAt,
                        evidencePhrase,
                        null
                );
            }

            public static MailStatusScanResult tokenRefreshFailed(String reason) {
                return failed(STATUS_TOKEN_REFRESH_FAILED, reason);
            }

            public static MailStatusScanResult microsoftAccountAbuse() {
                return failed(STATUS_MICROSOFT_ACCOUNT_ABUSE,
                        MicrosoftImapAccessTokenClient.REASON_MICROSOFT_ACCOUNT_ABUSE);
            }

            public static MailStatusScanResult imapAuthFailed() {
                return failed(STATUS_IMAP_AUTH_FAILED, "imap_auth_failed");
            }

            public static MailStatusScanResult imapError(String reason) {
                return failed(STATUS_IMAP_ERROR, reason);
            }

            public static MailStatusScanResult timeout() {
                return failed(STATUS_MAIL_SCAN_TIMEOUT, "mail_scan_timeout");
            }

            public static MailStatusScanResult failed(String status, String reason) {
                return new MailStatusScanResult(
                        status,
                        reason,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
            }

            public MailStatusScanResult withImapRoute(String imapRoute) {
                return new MailStatusScanResult(
                        status,
                        reason,
                        openaiMailFound,
                        folderName,
                        sender,
                        subject,
                        receivedAt,
                        evidencePhrase,
                        imapRoute
                );
            }
        }

    public MailStatusScanResult checkStatus(String email,
                                            String clientId,
                                            String refreshToken,
                                            Duration scanTimeout);
}
