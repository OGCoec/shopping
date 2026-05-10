package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.dto.AdminIp2LocationMailBatchRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationRegistrationCheckItem;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationRegistrationCheckResponse;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationVerifyLinkItem;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationVerifyLinksResponse;
import com.example.ShoppingSystem.tools.ip2location.verify.Ip2LocationVerifyMailReaderService;
import com.example.ShoppingSystem.tools.ip2location.verify.model.MailCredentials;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AdminIp2LocationMailToolService {

    private static final int MAX_THREAD_POOL_SIZE = 16;
    private static final int DEFAULT_THREAD_POOL_SIZE = 4;
    private static final int MAX_REGISTRATION_CHECK_ITEMS = 100;
    private static final int MAX_VERIFY_LINK_ITEMS = 10;

    private final Ip2LocationVerifyMailReaderService verifyMailReaderService;

    public AdminIp2LocationMailToolService(Ip2LocationVerifyMailReaderService verifyMailReaderService) {
        this.verifyMailReaderService = verifyMailReaderService;
    }

    public AdminIp2LocationRegistrationCheckResponse checkRegistration(AdminIp2LocationMailBatchRequest request) {
        List<CredentialInput> inputs = credentialInputs(request, MAX_REGISTRATION_CHECK_ITEMS);
        int threadPoolSize = resolveThreadPoolSize(request == null ? null : request.threadPoolSize(), inputs.size());
        List<AdminIp2LocationRegistrationCheckItem> results = runRegistrationChecks(inputs, threadPoolSize);
        return new AdminIp2LocationRegistrationCheckResponse(
                inputs.size(),
                threadPoolSize,
                results.stream().filter(AdminIp2LocationRegistrationCheckItem::registered).toList(),
                results.stream().filter(item -> !item.registered() && isNotRegisteredReason(item.reason())).toList(),
                results.stream().filter(item -> !item.registered() && !isNotRegisteredReason(item.reason())).toList()
        );
    }

    public AdminIp2LocationVerifyLinksResponse readVerifyLinks(AdminIp2LocationMailBatchRequest request) {
        List<CredentialInput> inputs = credentialInputs(request, MAX_VERIFY_LINK_ITEMS);
        int threadPoolSize = resolveThreadPoolSize(request == null ? null : request.threadPoolSize(), inputs.size());
        List<AdminIp2LocationVerifyLinkItem> results = runVerifyLinkReads(inputs, threadPoolSize);
        return new AdminIp2LocationVerifyLinksResponse(
                inputs.size(),
                threadPoolSize,
                results.stream().filter(item -> StringUtils.hasText(item.verifyUrl())).toList(),
                results.stream().filter(item -> !StringUtils.hasText(item.verifyUrl()) && isVerifyLinkNotFoundReason(item.reason())).toList(),
                results.stream().filter(item -> !StringUtils.hasText(item.verifyUrl()) && !isVerifyLinkNotFoundReason(item.reason())).toList()
        );
    }

    private List<AdminIp2LocationRegistrationCheckItem> runRegistrationChecks(List<CredentialInput> inputs,
                                                                              int threadPoolSize) {
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        try {
            List<CompletableFuture<AdminIp2LocationRegistrationCheckItem>> futures = inputs.stream()
                    .map(input -> CompletableFuture.supplyAsync(() -> checkRegistration(input), executor))
                    .toList();
            return futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
        } finally {
            executor.shutdown();
        }
    }

    private List<AdminIp2LocationVerifyLinkItem> runVerifyLinkReads(List<CredentialInput> inputs,
                                                                    int threadPoolSize) {
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        try {
            List<CompletableFuture<AdminIp2LocationVerifyLinkItem>> futures = inputs.stream()
                    .map(input -> CompletableFuture.supplyAsync(() -> readVerifyLink(input), executor))
                    .toList();
            return futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
        } finally {
            executor.shutdown();
        }
    }

    private AdminIp2LocationRegistrationCheckItem checkRegistration(CredentialInput input) {
        if (!input.valid()) {
            return registrationFailed(input, input.reason());
        }
        try {
            Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult result =
                    verifyMailReaderService.checkRegistrationMailTrace(
                            input.email(),
                            input.clientId(),
                            input.refreshToken()
                    );
            return new AdminIp2LocationRegistrationCheckItem(
                    input.lineNumber(),
                    input.email(),
                    maskValue(input.clientId()),
                    result.success(),
                    result.folderName(),
                    result.sender(),
                    result.subject(),
                    result.receivedAt(),
                    result.reason()
            );
        } catch (Exception ex) {
            return registrationFailed(input, "mail_check_error");
        }
    }

    private AdminIp2LocationVerifyLinkItem readVerifyLink(CredentialInput input) {
        if (!input.valid()) {
            return verifyLinkFailed(input, input.reason());
        }
        try {
            Ip2LocationVerifyMailReaderService.VerifyLinkReadResult result =
                    verifyMailReaderService.readLatestVerifyLink(
                            input.email(),
                            input.clientId(),
                            input.refreshToken()
                    );
            return new AdminIp2LocationVerifyLinkItem(
                    input.lineNumber(),
                    input.email(),
                    maskValue(input.clientId()),
                    result.folderName(),
                    result.sender(),
                    result.subject(),
                    result.receivedAt(),
                    result.verifyUrl(),
                    result.verifyToken(),
                    result.reason()
            );
        } catch (Exception ex) {
            return verifyLinkFailed(input, "verify_link_read_error");
        }
    }

    private AdminIp2LocationRegistrationCheckItem registrationFailed(CredentialInput input, String reason) {
        return new AdminIp2LocationRegistrationCheckItem(
                input.lineNumber(),
                input.email(),
                maskValue(input.clientId()),
                false,
                null,
                null,
                null,
                null,
                reason
        );
    }

    private AdminIp2LocationVerifyLinkItem verifyLinkFailed(CredentialInput input, String reason) {
        return new AdminIp2LocationVerifyLinkItem(
                input.lineNumber(),
                input.email(),
                maskValue(input.clientId()),
                null,
                null,
                null,
                null,
                null,
                null,
                reason
        );
    }

    private List<CredentialInput> credentialInputs(AdminIp2LocationMailBatchRequest request, int maxItems) {
        List<String> rawLines = request == null || request.credentialLines() == null
                ? List.of()
                : request.credentialLines();
        List<String> lines = rawLines.stream()
                .map(line -> line == null ? "" : line.trim())
                .filter(StringUtils::hasText)
                .toList();
        if (lines.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_IP2LOCATION_MAIL_CREDENTIALS_EMPTY",
                    "请至少填写一行邮箱凭证。",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (lines.size() > maxItems) {
            throw new AdminServiceException(
                    "ADMIN_IP2LOCATION_MAIL_CREDENTIALS_TOO_MANY",
                    "本次最多处理 " + maxItems + " 行邮箱凭证。",
                    HttpStatus.BAD_REQUEST
            );
        }
        List<CredentialInput> inputs = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index += 1) {
            inputs.add(parseCredentialInput(index + 1, lines.get(index)));
        }
        return inputs;
    }

    private CredentialInput parseCredentialInput(int lineNumber, String line) {
        try {
            MailCredentials credentials = MailCredentials.parse(line);
            if (!StringUtils.hasText(credentials.email())) {
                return CredentialInput.invalid(lineNumber, "", "", "invalid_email");
            }
            if (!StringUtils.hasText(credentials.clientId())) {
                return CredentialInput.invalid(lineNumber, credentials.email(), "", "invalid_client_id");
            }
            if (!StringUtils.hasText(credentials.refreshToken())) {
                return CredentialInput.invalid(lineNumber, credentials.email(), credentials.clientId(), "invalid_refresh_token");
            }
            return CredentialInput.valid(
                    lineNumber,
                    credentials.email().trim(),
                    credentials.clientId().trim(),
                    credentials.refreshToken().trim()
            );
        } catch (IllegalArgumentException ex) {
            return CredentialInput.invalid(lineNumber, "", "", "invalid_credential_format");
        }
    }

    private int resolveThreadPoolSize(Integer requestedThreadPoolSize, int itemCount) {
        int requested = requestedThreadPoolSize == null ? DEFAULT_THREAD_POOL_SIZE : requestedThreadPoolSize;
        int safeRequested = Math.max(1, requested);
        return Math.max(1, Math.min(Math.min(safeRequested, MAX_THREAD_POOL_SIZE), itemCount));
    }

    private boolean isNotRegisteredReason(String reason) {
        String normalized = normalizeReason(reason);
        return "ip2location_sender_not_found".equals(normalized)
                || normalized.startsWith("ip2location_sender_not_found_in_");
    }

    private boolean isVerifyLinkNotFoundReason(String reason) {
        String normalized = normalizeReason(reason);
        return "verify_link_not_found".equals(normalized)
                || normalized.startsWith("verify_link_not_found_in_");
    }

    private String normalizeReason(String reason) {
        return reason == null ? "" : reason.trim().toLowerCase(Locale.ROOT);
    }

    private String maskValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "";
        }
        String value = rawValue.trim();
        int length = value.length();
        if (length <= 4) {
            return "****";
        }
        if (length <= 8) {
            return value.substring(0, 2) + "****" + value.substring(length - 2);
        }
        return value.substring(0, 4) + "****" + value.substring(length - 4);
    }

    private record CredentialInput(int lineNumber,
                                   String email,
                                   String clientId,
                                   String refreshToken,
                                   boolean valid,
                                   String reason) {
        private static CredentialInput valid(int lineNumber, String email, String clientId, String refreshToken) {
            return new CredentialInput(lineNumber, email, clientId, refreshToken, true, "ok");
        }

        private static CredentialInput invalid(int lineNumber, String email, String clientId, String reason) {
            return new CredentialInput(lineNumber, email, clientId, "", false, reason);
        }
    }
}
