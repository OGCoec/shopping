package com.example.ShoppingSystem.service.user.auth.risk.scheduler;

import com.example.ShoppingSystem.mapper.risk.UserRiskAccountTerminationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RiskTerminatedIdentityCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RiskTerminatedIdentityCleanupScheduler.class);
    private static final int MAX_BATCH_SIZE = 5000;

    private final UserRiskAccountTerminationMapper userRiskAccountTerminationMapper;
    private final int batchSize;
    private final AtomicBoolean cleanupRunning = new AtomicBoolean(false);

    public RiskTerminatedIdentityCleanupScheduler(UserRiskAccountTerminationMapper userRiskAccountTerminationMapper,
                                                  @Value("${app.risk-terminated-identity-cleanup.batch-size:500}") int batchSize) {
        this.userRiskAccountTerminationMapper = userRiskAccountTerminationMapper;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.risk-terminated-identity-cleanup.cron:0 0 0/6 * * ?}")
    public void cleanupExpiredRiskTerminatedIdentities() {
        if (!cleanupRunning.compareAndSet(false, true)) {
            log.info("Risk terminated identity cleanup skipped, previous run is still active.");
            return;
        }
        int safeBatchSize = Math.max(1, Math.min(MAX_BATCH_SIZE, batchSize));
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(7);
        int batchCount = 0;
        long totalDeleted = 0L;
        boolean failed = false;
        try {
            while (true) {
                int deleted;
                try {
                    deleted = userRiskAccountTerminationMapper.deleteExpiredRiskTerminatedIdentities(cutoff, safeBatchSize);
                } catch (Exception e) {
                    failed = true;
                    log.warn("Risk terminated identity cleanup batch failed, batch={}, cutoff={}, batchSize={}, totalDeleted={}, reason={}",
                            batchCount + 1, cutoff, safeBatchSize, totalDeleted, e.getMessage());
                    break;
                }
                if (deleted <= 0) {
                    break;
                }
                batchCount++;
                totalDeleted += deleted;
                if (deleted < safeBatchSize) {
                    break;
                }
            }
        } finally {
            cleanupRunning.set(false);
        }

        if (totalDeleted > 0 || failed) {
            log.info("Risk terminated identity cleanup finished, batches={}, totalDeleted={}, cutoff={}, batchSize={}, failed={}",
                    batchCount, totalDeleted, cutoff, safeBatchSize, failed);
        }
    }
}
