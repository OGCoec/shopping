package com.example.ShoppingSystem.service.user.auth.risk.scheduler;

import com.example.ShoppingSystem.service.user.auth.risk.UserRiskLockRecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class UserRiskLockRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserRiskLockRecoveryScheduler.class);
    private static final String NETWORK_LOCK_REASON = "NETWORK_RISK_LOCK_30M";
    private static final String NETWORK_RECOVERY_EVENT = "NETWORK_RISK_RECOVERY";
    private static final int MAX_BATCH_SIZE = 4000;

    private final UserRiskLockRecoveryService userRiskLockRecoveryService;
    private final int batchSize;

    public UserRiskLockRecoveryScheduler(UserRiskLockRecoveryService userRiskLockRecoveryService,
                                         @Value("${app.user-risk-lock-recovery.batch-size:1000}") int batchSize) {
        this.userRiskLockRecoveryService = userRiskLockRecoveryService;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.user-risk-lock-recovery.lock-count-1-cron:0 0 */3 * * ?}")
    public void recoverFirstLockStableUsers() {
        recover(1, Duration.ofDays(7), 400);
    }

    @Scheduled(cron = "${app.user-risk-lock-recovery.lock-count-2-cron:0 0 */6 * * ?}")
    public void recoverSecondLockStableUsers() {
        recover(2, Duration.ofDays(14), 600);
    }

    @Scheduled(cron = "${app.user-risk-lock-recovery.lock-count-3-cron:0 0 0 * * ?}")
    public void recoverThirdLockStableUsers() {
        recover(3, Duration.ofDays(30), 800);
    }

    @Scheduled(cron = "${app.user-risk-lock-recovery.network-lock-count-1-cron:0 30 */3 * * ?}")
    public void recoverFirstNetworkLockStableUsers() {
        recoverNetwork(1, Duration.ofDays(7), 400);
    }

    @Scheduled(cron = "${app.user-risk-lock-recovery.network-lock-count-2-cron:0 30 */6 * * ?}")
    public void recoverSecondNetworkLockStableUsers() {
        recoverNetwork(2, Duration.ofDays(14), 300);
    }

    @Scheduled(cron = "${app.user-risk-lock-recovery.network-lock-count-3-cron:0 30 0 * * ?}")
    public void recoverThirdNetworkLockStableUsers() {
        recoverNetwork(3, Duration.ofDays(30), 200);
    }

    private void recover(int lockCount, Duration stableDuration, int scoreBonus) {
        recoverBatches("User risk lock recovery",
                lockCount,
                stableDuration,
                scoreBonus,
                effectiveBatchSize -> userRiskLockRecoveryService.recoverStableUnlockedUsers(
                        lockCount,
                        stableDuration,
                        scoreBonus,
                        effectiveBatchSize
                ));
    }

    private void recoverNetwork(int lockCount, Duration stableDuration, int scoreBonus) {
        recoverBatches("User network risk lock recovery",
                lockCount,
                stableDuration,
                scoreBonus,
                effectiveBatchSize -> userRiskLockRecoveryService.recoverStableUnlockedUsersByReason(
                        NETWORK_LOCK_REASON,
                        NETWORK_RECOVERY_EVENT,
                        NETWORK_RECOVERY_EVENT,
                        lockCount,
                        stableDuration,
                        scoreBonus,
                        effectiveBatchSize
                ));
    }

    private void recoverBatches(String recoveryName,
                                int lockCount,
                                Duration stableDuration,
                                int scoreBonus,
                                RecoveryBatch recoveryBatch) {
        int effectiveBatchSize = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        int batchCount = 0;
        long totalRecovered = 0L;
        boolean failed = false;
        while (true) {
            int recovered;
            try {
                recovered = recoveryBatch.recover(effectiveBatchSize);
            } catch (Exception e) {
                failed = true;
                log.warn("{} batch failed, lockCount={}, stableDays={}, scoreBonus={}, batch={}, batchSize={}, totalRecovered={}, error={}",
                        recoveryName,
                        lockCount,
                        stableDuration.toDays(),
                        scoreBonus,
                        batchCount + 1,
                        effectiveBatchSize,
                        totalRecovered,
                        e.getMessage());
                break;
            }
            if (recovered <= 0) {
                break;
            }

            batchCount++;
            totalRecovered += recovered;
            if (recovered < effectiveBatchSize) {
                break;
            }
        }

        if (totalRecovered > 0 || failed) {
            log.info("{} finished, lockCount={}, stableDays={}, scoreBonus={}, batches={}, totalRecovered={}, batchSize={}, failed={}",
                    recoveryName,
                    lockCount,
                    stableDuration.toDays(),
                    scoreBonus,
                    batchCount,
                    totalRecovered,
                    effectiveBatchSize,
                    failed);
        }
    }

    @FunctionalInterface
    private interface RecoveryBatch {
        int recover(int effectiveBatchSize);
    }
}
