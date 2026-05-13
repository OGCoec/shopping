package com.example.ShoppingSystem.service.user.auth.risk.scheduler;

import com.example.ShoppingSystem.mapper.UserRiskAccountTerminationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class RiskTerminatedIdentityCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RiskTerminatedIdentityCleanupScheduler.class);

    private final UserRiskAccountTerminationMapper userRiskAccountTerminationMapper;
    private final int batchSize;

    public RiskTerminatedIdentityCleanupScheduler(UserRiskAccountTerminationMapper userRiskAccountTerminationMapper,
                                                  @Value("${app.risk-terminated-identity-cleanup.batch-size:500}") int batchSize) {
        this.userRiskAccountTerminationMapper = userRiskAccountTerminationMapper;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.risk-terminated-identity-cleanup.cron:0 0 0/6 * * ?}")
    public void cleanupExpiredRiskTerminatedIdentities() {
        int safeBatchSize = Math.max(1, Math.min(5000, batchSize));
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(7);
        try {
            int deleted = userRiskAccountTerminationMapper.deleteExpiredRiskTerminatedIdentities(cutoff, safeBatchSize);
            log.info("Risk terminated identity cleanup completed, deleted={}, cutoff={}, batchSize={}",
                    deleted, cutoff, safeBatchSize);
        } catch (Exception e) {
            log.warn("Risk terminated identity cleanup failed, cutoff={}, batchSize={}, reason={}",
                    cutoff, safeBatchSize, e.getMessage());
        }
    }
}
