package com.example.ShoppingSystem.service.user.profile.scheduler;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBoundCountingBloomService;
import com.example.ShoppingSystem.service.user.profile.UserAccountDeletionMessagePublisher;
import com.example.ShoppingSystem.service.user.profile.UserAccountDeletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class UserAccountDeletionCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserAccountDeletionCleanupScheduler.class);
    private static final int MAX_BATCH_SIZE = 500;

    private final UserAccountDeletionService userAccountDeletionService;
    private final UserAccountDeletionMessagePublisher userAccountDeletionMessagePublisher;
    private final PhoneBoundCountingBloomService phoneBoundCountingBloomService;
    private final int batchSize;
    private final AtomicBoolean cleanupRunning = new AtomicBoolean(false);

    public UserAccountDeletionCleanupScheduler(UserAccountDeletionService userAccountDeletionService,
                                               UserAccountDeletionMessagePublisher userAccountDeletionMessagePublisher,
                                               PhoneBoundCountingBloomService phoneBoundCountingBloomService,
                                               @Value("${app.user-account-deletion.cleanup-batch-size:100}") int batchSize) {
        this.userAccountDeletionService = userAccountDeletionService;
        this.userAccountDeletionMessagePublisher = userAccountDeletionMessagePublisher;
        this.phoneBoundCountingBloomService = phoneBoundCountingBloomService;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.user-account-deletion.cleanup-cron:0 0 0/2 * * ?}")
    public void cleanupExpiredSelfDeletions() {
        if (!cleanupRunning.compareAndSet(false, true)) {
            log.info("User account self deletion cleanup skipped, previous run is still active.");
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(7);
        int effectiveBatchSize = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        int batchCount = 0;
        int totalCleaned = 0;
        long totalPhonesRequested = 0L;
        long totalPhonesRemoved = 0L;
        boolean failed = false;
        try {
            while (true) {
                List<UserAccountDeletionService.MailTarget> targets;
                try {
                    targets = userAccountDeletionService.completeExpiredSelfDeletionsBatch(cutoff, effectiveBatchSize);
                } catch (Exception e) {
                    failed = true;
                    log.warn("User account self deletion cleanup db batch failed, batch={}, cutoff={}, batchSize={}, totalCleaned={}, error={}",
                            batchCount + 1, cutoff, effectiveBatchSize, totalCleaned, e.getMessage());
                    break;
                }
                if (targets == null || targets.isEmpty()) {
                    break;
                }

                batchCount++;
                totalCleaned += targets.size();
                CleanupBatchResult result = handleCleanupTargets(targets);
                totalPhonesRequested += result.phoneRequestCount();
                totalPhonesRemoved += result.phoneRemovedCount();

                if (targets.size() < effectiveBatchSize) {
                    break;
                }
            }
        } finally {
            cleanupRunning.set(false);
        }

        if (totalCleaned == 0 && !failed) {
            log.info("User account self deletion cleanup skipped, no expired self deletion, cutoff={}, batchSize={}",
                    cutoff, effectiveBatchSize);
            return;
        }
        log.info("User account self deletion cleanup finished, batches={}, totalCleaned={}, totalPhonesRequested={}, totalPhonesRemoved={}, cutoff={}, failed={}",
                batchCount, totalCleaned, totalPhonesRequested, totalPhonesRemoved, cutoff, failed);
    }

    private CleanupBatchResult handleCleanupTargets(List<UserAccountDeletionService.MailTarget> targets) {
        List<String> phones = targets.stream()
                .map(UserAccountDeletionService.MailTarget::phone)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        long removedPhones = 0L;
        try {
            removedPhones = phoneBoundCountingBloomService.removeVerifiedPhones(phones);
            log.info("Phone-bound counting bloom cleanup completed, requested={}, removed={}",
                    phones.size(), removedPhones);
        } catch (Exception e) {
            log.warn("Phone-bound counting bloom cleanup failed, requested={}, error={}", phones.size(), e.getMessage());
        }
        for (UserAccountDeletionService.MailTarget target : targets) {
            if (target == null) {
                continue;
            }
            try {
                userAccountDeletionMessagePublisher.publishSelfDeletionCompleted(target.userId(), target.email());
            } catch (Exception e) {
                log.warn("User account self deletion completed mail publish failed, userId={}, error={}",
                        target.userId(), e.getMessage());
            }
        }
        return new CleanupBatchResult(phones.size(), removedPhones);
    }

    private record CleanupBatchResult(long phoneRequestCount, long phoneRemovedCount) {
    }
}
