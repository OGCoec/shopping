package com.example.ShoppingSystem.tools.kiro.mail;

import com.example.ShoppingSystem.common.proxy.LocalProxyResolver;
import com.example.ShoppingSystem.tools.ip2location.verify.imap.ImapFolderScanPlanner;
import com.example.ShoppingSystem.tools.ip2location.verify.oauth.MicrosoftImapAccessTokenClient;
import com.example.ShoppingSystem.tools.openai.mail.OpenAiMailBodyExtractor;
import com.example.ShoppingSystem.tools.openai.mail.OpenAiMailImapScanner;
import com.example.ShoppingSystem.tools.openai.mail.OpenAiMailMatcher;
import com.example.ShoppingSystem.tools.openai.mail.OpenAiMailStatusReaderService;
import com.example.ShoppingSystem.tools.openai.mail.OpenAiMailStatusReaderService.MailStatusScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

public interface KiroMailStatusReaderService {
    public static final String STATUS_NOT_REGISTERED = "KIRO_NOT_REGISTERED";

    public static final String STATUS_REGISTERED_NORMAL = "KIRO_REGISTERED_NORMAL";

    public static final String STATUS_RESTRICTED_EVIDENCE_FOUND = "KIRO_RESTRICTED_EVIDENCE_FOUND";

    public static final String STATUS_MICROSOFT_ACCOUNT_ABUSE = "MICROSOFT_ACCOUNT_ABUSE";

    public static final String STATUS_TOKEN_REFRESH_FAILED = "TOKEN_REFRESH_FAILED";

    public static final String STATUS_IMAP_AUTH_FAILED = "IMAP_AUTH_FAILED";

    public static final String STATUS_IMAP_ERROR = "IMAP_ERROR";

    public static final String STATUS_MAIL_SCAN_TIMEOUT = "MAIL_SCAN_TIMEOUT";

    public record KiroMailStatusScanResult(String status,
                                               String reason,
                                               boolean mailFound,
                                               String folderName,
                                               String sender,
                                               String subject,
                                               String receivedAt,
                                               String evidencePhrase,
                                               String imapRoute) {
            public static KiroMailStatusScanResult notRegistered(String imapRoute) {
                return new KiroMailStatusScanResult(
                        STATUS_NOT_REGISTERED,
                        "kiro_or_amazonaws_mail_not_found",
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        imapRoute
                );
            }

            public static KiroMailStatusScanResult registeredNormal(String folderName,
                                                                    String sender,
                                                                    String subject,
                                                                    String receivedAt,
                                                                    String imapRoute) {
                return new KiroMailStatusScanResult(
                        STATUS_REGISTERED_NORMAL,
                        "kiro_mail_found_no_restricted_evidence",
                        true,
                        folderName,
                        sender,
                        subject,
                        receivedAt,
                        null,
                        imapRoute
                );
            }

            public static KiroMailStatusScanResult restrictedEvidenceFound(String folderName,
                                                                           String sender,
                                                                           String subject,
                                                                           String receivedAt,
                                                                           String evidencePhrase,
                                                                           String imapRoute) {
                return new KiroMailStatusScanResult(
                        STATUS_RESTRICTED_EVIDENCE_FOUND,
                        "restricted_phrase_found",
                        true,
                        folderName,
                        sender,
                        subject,
                        receivedAt,
                        evidencePhrase,
                        imapRoute
                );
            }

            public static KiroMailStatusScanResult tokenRefreshFailed(String reason) {
                return failed(STATUS_TOKEN_REFRESH_FAILED, reason, null);
            }

            public static KiroMailStatusScanResult microsoftAccountAbuse() {
                return failed(STATUS_MICROSOFT_ACCOUNT_ABUSE,
                        MicrosoftImapAccessTokenClient.REASON_MICROSOFT_ACCOUNT_ABUSE,
                        null);
            }

            public static KiroMailStatusScanResult imapError(String reason) {
                return failed(STATUS_IMAP_ERROR, reason, null);
            }

            public static KiroMailStatusScanResult timeout() {
                return failed(STATUS_MAIL_SCAN_TIMEOUT, "mail_scan_timeout", null);
            }

            public static KiroMailStatusScanResult failed(String status, String reason, String imapRoute) {
                return new KiroMailStatusScanResult(
                        status,
                        reason,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        imapRoute
                );
            }
        }

    public KiroMailStatusScanResult checkStatus(String email,
                                                String clientId,
                                                String refreshToken,
                                                Duration scanTimeout);
}
