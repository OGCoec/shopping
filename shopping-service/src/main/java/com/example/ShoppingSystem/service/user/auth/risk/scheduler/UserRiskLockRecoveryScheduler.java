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

    private void recover(int lockCount, Duration stableDuration, int scoreBonus) {
        int recovered = userRiskLockRecoveryService.recoverStableUnlockedUsers(
                lockCount,
                stableDuration,
                scoreBonus,
                batchSize
        );
        if (recovered > 0) {
            log.info("User risk lock recovery completed, lockCount={}, stableDays={}, scoreBonus={}, recovered={}",
                    lockCount,
                    stableDuration.toDays(),
                    scoreBonus,
                    recovered);
        }
    }
}
