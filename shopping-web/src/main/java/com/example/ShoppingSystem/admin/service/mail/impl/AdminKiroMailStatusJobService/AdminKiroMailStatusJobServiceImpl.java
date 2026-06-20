package com.example.ShoppingSystem.admin.service.mail.impl.AdminKiroMailStatusJobService;

import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusCheckRequest;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusJobCreateResponse;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusJobResponse;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusResultItem;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusSummary;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.tools.ip2location.verify.model.MailCredentials;
import com.example.ShoppingSystem.tools.kiro.mail.KiroMailStatusReaderService;
import com.example.ShoppingSystem.tools.kiro.mail.KiroMailStatusReaderService.KiroMailStatusScanResult;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.example.ShoppingSystem.admin.service.mail.AdminKiroMailStatusJobService;
@Service
public class AdminKiroMailStatusJobServiceImpl implements AdminKiroMailStatusJobService {

    private static final String JOB_STATUS_RUNNING = "RUNNING";
    private static final String JOB_STATUS_COMPLETED = "COMPLETED";
    private static final String RESULT_STATUS_DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
    private static final String RESULT_STATUS_INVALID_CREDENTIAL_FORMAT = "INVALID_CREDENTIAL_FORMAT";
    private static final int MAX_CREDENTIAL_LINES = 100;
    private static final int MAX_THREAD_POOL_SIZE = 64;
    private static final int DEFAULT_THREAD_POOL_SIZE = 4;
    private static final int MAX_PER_ACCOUNT_TIMEOUT_SECONDS = 600;
    private static final long POLL_AFTER_MILLIS = 2000L;
    private static final DateTimeFormatter JOB_ID_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final KiroMailStatusReaderService readerService;
    private final int perAccountTimeoutSeconds;
    private final ConcurrentMap<String, JobState> jobs = new ConcurrentHashMap<>();
    private final AtomicReference<String> runningJobId = new AtomicReference<>();
    private final AtomicLong jobSequence = new AtomicLong();
    private final ExecutorService coordinatorExecutor =
            Executors.newSingleThreadExecutor(namedThreadFactory("admin-kiro-mail-job-"));

    public AdminKiroMailStatusJobServiceImpl(
            KiroMailStatusReaderService readerService,
            @Value("${kiro.status-mail.per-account-timeout-seconds:600}") int perAccountTimeoutSeconds) {
        this.readerService = readerService;
        this.perAccountTimeoutSeconds = Math.max(1,
                Math.min(perAccountTimeoutSeconds, MAX_PER_ACCOUNT_TIMEOUT_SECONDS));
    }

    public AdminKiroMailStatusJobCreateResponse createJob(AdminKiroMailStatusCheckRequest request) {
        List<String> lines = credentialLines(request);
        PreparedInputs preparedInputs = prepareInputs(lines);
        int threadPoolSize = resolveThreadPoolSize(
                request == null ? null : request.threadPoolSize(),
                preparedInputs.scanInputs().size()
        );
        String jobId = nextJobId();
        Instant now = Instant.now();
        JobState job = new JobState(
                jobId,
                lines.size(),
                preparedInputs.scanInputs().size(),
                preparedInputs.duplicateCount(),
                preparedInputs.invalidCount(),
                threadPoolSize,
                now
        );
        preparedInputs.immediateResults().forEach(job::recordResult);

        jobs.put(jobId, job);
        if (!runningJobId.compareAndSet(null, jobId)) {
            jobs.remove(jobId);
            throw new AdminServiceException(
                    "ADMIN_KIRO_MAIL_JOB_RUNNING",
                    "A Kiro mailbox status job is already running. Wait for it to complete.",
                    HttpStatus.CONFLICT
            );
        }
        coordinatorExecutor.submit(() -> runJob(job, preparedInputs.scanInputs()));
        return new AdminKiroMailStatusJobCreateResponse(
                jobId,
                job.status(),
                job.requestedCount(),
                job.acceptedCount(),
                job.duplicateCount(),
                job.invalidCount(),
                job.threadPoolSize(),
                MAX_THREAD_POOL_SIZE,
                perAccountTimeoutSeconds,
                toIsoString(job.createdAt()),
                POLL_AFTER_MILLIS
        );
    }

    public AdminKiroMailStatusJobResponse getJob(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            throw new AdminServiceException(
                    "ADMIN_KIRO_MAIL_JOB_ID_EMPTY",
                    "Kiro mailbox status job ID is required.",
                    HttpStatus.BAD_REQUEST
            );
        }
        JobState job = jobs.get(jobId.trim());
        if (job == null) {
            throw new AdminServiceException(
                    "ADMIN_KIRO_MAIL_JOB_NOT_FOUND",
                    "Kiro mailbox status job does not exist.",
                    HttpStatus.NOT_FOUND
            );
        }
        return job.snapshot();
    }

    private void runJob(JobState job, List<CredentialInput> scanInputs) {
        ExecutorService scanExecutor = Executors.newFixedThreadPool(
                Math.max(1, job.threadPoolSize()),
                namedThreadFactory("admin-kiro-mail-scan-")
        );
        job.markStarted();
        try {
            List<CompletableFuture<Void>> futures = scanInputs.stream()
                    .map(input -> CompletableFuture
                            .supplyAsync(() -> checkOne(job, input), scanExecutor)
                            .exceptionally(error -> failedFromAsyncError(input))
                            .thenAccept(job::recordResult))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            job.markCompleted();
            scanExecutor.shutdownNow();
            runningJobId.compareAndSet(job.jobId(), null);
        }
    }

    private AdminKiroMailStatusResultItem checkOne(JobState job, CredentialInput input) {
        job.markScanStarted();
        try {
            KiroMailStatusScanResult result = readerService.checkStatus(
                    input.email(),
                    input.clientId(),
                    input.refreshToken(),
                    Duration.ofSeconds(perAccountTimeoutSeconds)
            );
            return new AdminKiroMailStatusResultItem(
                    input.lineNumber(),
                    input.email(),
                    result.status(),
                    result.mailFound(),
                    result.folderName(),
                    result.sender(),
                    result.subject(),
                    result.receivedAt(),
                    result.evidencePhrase(),
                    result.imapRoute(),
                    result.reason()
            );
        } catch (Exception ex) {
            return failedItem(input.lineNumber(), input.email(), KiroMailStatusReaderService.STATUS_IMAP_ERROR,
                    "kiro_mail_check_error");
        } finally {
            job.markScanFinished();
        }
    }

    private AdminKiroMailStatusResultItem failedFromAsyncError(CredentialInput input) {
        return failedItem(input.lineNumber(), input.email(), KiroMailStatusReaderService.STATUS_IMAP_ERROR,
                "kiro_mail_check_error");
    }

    private List<String> credentialLines(AdminKiroMailStatusCheckRequest request) {
        List<String> rawLines = request == null || request.credentialLines() == null
                ? List.of()
                : request.credentialLines();
        List<String> lines = rawLines.stream()
                .map(line -> line == null ? "" : line.trim())
                .filter(StringUtils::hasText)
                .toList();
        if (lines.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_KIRO_MAIL_CREDENTIALS_EMPTY",
                    "Please provide at least one mailbox credential line.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (lines.size() > MAX_CREDENTIAL_LINES) {
            throw new AdminServiceException(
                    "ADMIN_KIRO_MAIL_CREDENTIALS_TOO_MANY",
                    "At most " + MAX_CREDENTIAL_LINES + " mailbox credential lines can be checked at once.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return lines;
    }

    private PreparedInputs prepareInputs(List<String> lines) {
        List<CredentialInput> scanInputs = new ArrayList<>();
        List<AdminKiroMailStatusResultItem> immediateResults = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        int duplicateCount = 0;
        int invalidCount = 0;
        for (int index = 0; index < lines.size(); index += 1) {
            int lineNumber = index + 1;
            CredentialParseResult parseResult = parseCredential(lines.get(index));
            if (!parseResult.valid()) {
                invalidCount += 1;
                immediateResults.add(failedItem(
                        lineNumber,
                        parseResult.email(),
                        RESULT_STATUS_INVALID_CREDENTIAL_FORMAT,
                        parseResult.reason()
                ));
                continue;
            }
            String normalizedEmail = parseResult.email().toLowerCase(Locale.ROOT);
            if (!seenEmails.add(normalizedEmail)) {
                duplicateCount += 1;
                immediateResults.add(failedItem(
                        lineNumber,
                        parseResult.email(),
                        RESULT_STATUS_DUPLICATE_EMAIL,
                        "duplicate_email"
                ));
                continue;
            }
            scanInputs.add(new CredentialInput(
                    lineNumber,
                    parseResult.email(),
                    parseResult.clientId(),
                    parseResult.refreshToken()
            ));
        }
        return new PreparedInputs(scanInputs, immediateResults, duplicateCount, invalidCount);
    }

    private CredentialParseResult parseCredential(String line) {
        try {
            MailCredentials credentials = MailCredentials.parse(line);
            String email = trimToNull(credentials.email());
            String clientId = trimToNull(credentials.clientId());
            String refreshToken = trimToNull(credentials.refreshToken());
            if (email == null) {
                return CredentialParseResult.invalid("", "invalid_email");
            }
            if (clientId == null) {
                return CredentialParseResult.invalid(email, "invalid_client_id");
            }
            if (refreshToken == null) {
                return CredentialParseResult.invalid(email, "invalid_refresh_token");
            }
            return CredentialParseResult.valid(email, clientId, refreshToken);
        } catch (IllegalArgumentException ex) {
            return CredentialParseResult.invalid("", "invalid_credential_format");
        }
    }

    private int resolveThreadPoolSize(Integer requestedThreadPoolSize, int itemCount) {
        if (itemCount <= 0) {
            return 1;
        }
        int requested = requestedThreadPoolSize == null ? DEFAULT_THREAD_POOL_SIZE : requestedThreadPoolSize;
        int safeRequested = Math.max(1, requested);
        return Math.max(1, Math.min(Math.min(safeRequested, MAX_THREAD_POOL_SIZE), itemCount));
    }

    private String nextJobId() {
        long sequence = jobSequence.incrementAndGet();
        return "kiro-mail-check-" + JOB_ID_TIME_FORMATTER.format(Instant.now())
                + "-" + String.format(Locale.ROOT, "%03d", sequence % 1000);
    }

    private AdminKiroMailStatusResultItem failedItem(int lineNumber,
                                                     String email,
                                                     String status,
                                                     String reason) {
        return new AdminKiroMailStatusResultItem(
                lineNumber,
                email == null ? "" : email,
                status,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                reason
        );
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String toIsoString(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    @PreDestroy
    public void shutdown() {
        coordinatorExecutor.shutdownNow();
    }

    private record CredentialInput(int lineNumber,
                                   String email,
                                   String clientId,
                                   String refreshToken) {
    }

    private record CredentialParseResult(boolean valid,
                                         String email,
                                         String clientId,
                                         String refreshToken,
                                         String reason) {
        private static CredentialParseResult valid(String email, String clientId, String refreshToken) {
            return new CredentialParseResult(true, email, clientId, refreshToken, "ok");
        }

        private static CredentialParseResult invalid(String email, String reason) {
            return new CredentialParseResult(false, email, "", "", reason);
        }
    }

    private record PreparedInputs(List<CredentialInput> scanInputs,
                                  List<AdminKiroMailStatusResultItem> immediateResults,
                                  int duplicateCount,
                                  int invalidCount) {
    }

    private final class JobState {
        private final String jobId;
        private final int requestedCount;
        private final int acceptedCount;
        private final int duplicateCount;
        private final int invalidCount;
        private final int threadPoolSize;
        private final Instant createdAt;
        private final ConcurrentMap<Integer, AdminKiroMailStatusResultItem> results = new ConcurrentHashMap<>();
        private final AtomicInteger startedScanCount = new AtomicInteger();
        private final AtomicInteger runningCount = new AtomicInteger();
        private volatile String status = JOB_STATUS_RUNNING;
        private volatile Instant startedAt;
        private volatile Instant completedAt;

        private JobState(String jobId,
                         int requestedCount,
                         int acceptedCount,
                         int duplicateCount,
                         int invalidCount,
                         int threadPoolSize,
                         Instant createdAt) {
            this.jobId = jobId;
            this.requestedCount = requestedCount;
            this.acceptedCount = acceptedCount;
            this.duplicateCount = duplicateCount;
            this.invalidCount = invalidCount;
            this.threadPoolSize = threadPoolSize;
            this.createdAt = createdAt;
        }

        private String jobId() {
            return jobId;
        }

        private int requestedCount() {
            return requestedCount;
        }

        private int acceptedCount() {
            return acceptedCount;
        }

        private int duplicateCount() {
            return duplicateCount;
        }

        private int invalidCount() {
            return invalidCount;
        }

        private int threadPoolSize() {
            return threadPoolSize;
        }

        private Instant createdAt() {
            return createdAt;
        }

        private String status() {
            return status;
        }

        private void markStarted() {
            this.startedAt = Instant.now();
            if (acceptedCount == 0) {
                markCompleted();
            }
        }

        private void markCompleted() {
            this.completedAt = Instant.now();
            this.status = JOB_STATUS_COMPLETED;
        }

        private void markScanStarted() {
            startedScanCount.incrementAndGet();
            runningCount.incrementAndGet();
        }

        private void markScanFinished() {
            runningCount.updateAndGet(value -> Math.max(0, value - 1));
        }

        private void recordResult(AdminKiroMailStatusResultItem item) {
            if (item != null) {
                results.put(item.lineNumber(), item);
            }
        }

        private AdminKiroMailStatusJobResponse snapshot() {
            List<AdminKiroMailStatusResultItem> orderedResults = results.values().stream()
                    .sorted(Comparator.comparingInt(AdminKiroMailStatusResultItem::lineNumber))
                    .toList();
            AdminKiroMailStatusSummary summary = summarize(orderedResults);
            boolean completed = JOB_STATUS_COMPLETED.equals(status);
            int activeRunningCount = completed ? 0 : runningCount.get();
            int queuedCount = completed ? 0 : Math.max(0, acceptedCount - startedScanCount.get());
            return new AdminKiroMailStatusJobResponse(
                    jobId,
                    status,
                    requestedCount,
                    orderedResults.size(),
                    activeRunningCount,
                    queuedCount,
                    threadPoolSize,
                    toIsoString(startedAt),
                    toIsoString(completedAt),
                    elapsedMillis(),
                    summary,
                    orderedResults
            );
        }

        private long elapsedMillis() {
            Instant start = startedAt == null ? createdAt : startedAt;
            Instant end = completedAt == null ? Instant.now() : completedAt;
            return Math.max(0L, Duration.between(start, end).toMillis());
        }

        private AdminKiroMailStatusSummary summarize(List<AdminKiroMailStatusResultItem> items) {
            int notRegistered = 0;
            int registeredNormal = 0;
            int detectedEvidenceFound = 0;
            int duplicate = 0;
            int failed = 0;
            for (AdminKiroMailStatusResultItem item : items) {
                String itemStatus = item.status();
                if (KiroMailStatusReaderService.STATUS_NOT_REGISTERED.equals(itemStatus)) {
                    notRegistered += 1;
                } else if (KiroMailStatusReaderService.STATUS_REGISTERED_NORMAL.equals(itemStatus)) {
                    registeredNormal += 1;
                } else if (KiroMailStatusReaderService.STATUS_RESTRICTED_EVIDENCE_FOUND.equals(itemStatus)) {
                    detectedEvidenceFound += 1;
                } else if (RESULT_STATUS_DUPLICATE_EMAIL.equals(itemStatus)) {
                    duplicate += 1;
                } else {
                    failed += 1;
                }
            }
            return new AdminKiroMailStatusSummary(
                    notRegistered,
                    registeredNormal,
                    detectedEvidenceFound,
                    duplicate,
                    failed
            );
        }
    }
}
