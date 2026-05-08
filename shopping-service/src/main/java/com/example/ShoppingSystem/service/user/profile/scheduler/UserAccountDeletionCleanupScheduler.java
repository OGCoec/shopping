package com.example.ShoppingSystem.service.user.profile.scheduler;

import com.example.ShoppingSystem.service.user.profile.UserAccountDeletionMessagePublisher;
import com.example.ShoppingSystem.service.user.profile.UserAccountDeletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class UserAccountDeletionCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserAccountDeletionCleanupScheduler.class);

    private final UserAccountDeletionService userAccountDeletionService;
    private final UserAccountDeletionMessagePublisher userAccountDeletionMessagePublisher;
    private final int batchSize;

    public UserAccountDeletionCleanupScheduler(UserAccountDeletionService userAccountDeletionService,
                                               UserAccountDeletionMessagePublisher userAccountDeletionMessagePublisher,
                                               @Value("${app.user-account-deletion.cleanup-batch-size:100}") int batchSize) {
        this.userAccountDeletionService = userAccountDeletionService;
        this.userAccountDeletionMessagePublisher = userAccountDeletionMessagePublisher;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.user-account-deletion.cleanup-cron:0 0 0/2 * * ?}")
    public void cleanupExpiredSelfDeletions() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(7);
        List<UserAccountDeletionService.MailTarget> targets;
        try {
            targets = userAccountDeletionService.completeExpiredSelfDeletionsBatch(cutoff, batchSize);
        } catch (Exception e) {
            log.warn("User account self deletion cleanup db batch failed, cutoff={}, error={}", cutoff, e.getMessage());
            return;
        }
        if (targets == null || targets.isEmpty()) {
            log.info("User account self deletion cleanup skipped, no expired self deletion, cutoff={}", cutoff);
            return;
        }
        log.info("User account self deletion cleanup completed db batch, count={}, cutoff={}", targets.size(), cutoff);
        for (UserAccountDeletionService.MailTarget target : targets) {
            try {
                userAccountDeletionMessagePublisher.publishSelfDeletionCompleted(target.userId(), target.email());
            } catch (Exception e) {
                log.warn("User account self deletion completed mail publish failed, userId={}, error={}",
                        target == null ? null : target.userId(),
                        e.getMessage());
            }
        }
    }
}
