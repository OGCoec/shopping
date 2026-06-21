package com.example.ShoppingSystem.security.risk.impl.AccountNetworkRiskService;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.transaction.AfterCommitExecutor;
import com.example.ShoppingSystem.mapper.risk.UserRiskAccountTerminationMapper;
import com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper;
import com.example.ShoppingSystem.outbox.OutboxEventRequest;
import com.example.ShoppingSystem.outbox.accountsync.AccountStatusSyncMessage;
import com.example.ShoppingSystem.outbox.accountsync.AccountStatusSyncRouting;
import com.example.ShoppingSystem.outbox.annotation.OutboxEventCollector;
import com.example.ShoppingSystem.outbox.annotation.TransactionalOutbox;
import com.example.ShoppingSystem.service.user.auth.risk.TerminatedAccountEmailBloomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class AccountNetworkRiskLockWriter {

    private static final Logger log = LoggerFactory.getLogger(AccountNetworkRiskLockWriter.class);

    private final UserRiskProfileMapper userRiskProfileMapper;
    private final UserRiskAccountTerminationMapper userRiskAccountTerminationMapper;
    private final TerminatedAccountEmailBloomService terminatedAccountEmailBloomService;
    private final OutboxEventCollector outboxEvents;

    public AccountNetworkRiskLockWriter(UserRiskProfileMapper userRiskProfileMapper,
                                        UserRiskAccountTerminationMapper userRiskAccountTerminationMapper,
                                        TerminatedAccountEmailBloomService terminatedAccountEmailBloomService,
                                        OutboxEventCollector outboxEvents) {
        this.userRiskProfileMapper = userRiskProfileMapper;
        this.userRiskAccountTerminationMapper = userRiskAccountTerminationMapper;
        this.terminatedAccountEmailBloomService = terminatedAccountEmailBloomService;
        this.outboxEvents = outboxEvents;
    }

    @TransactionalOutbox(DataSourceRoute.RISK)
    public void applyLockAndEnqueue(NetworkLockWrite write) {
        TerminationData termination = write.termination();
        if (termination != null) {
            userRiskAccountTerminationMapper.upsertRiskTermination(
                    termination.terminationId(),
                    write.userId(),
                    termination.email(),
                    termination.emailHash(),
                    termination.phone(),
                    termination.phoneHash(),
                    write.lockReason(),
                    write.now(),
                    write.now()
            );
            registerTerminatedEmailBloomSync(write.userId(), termination.emailHash());
        }

        userRiskProfileMapper.upsertUserAuthLockState(
                write.userId(),
                write.currentEnvScore(),
                write.nextBehaviorScoreDelta(),
                write.scoreAfter(),
                write.riskLevelAfter(),
                write.nextLockCount(),
                write.now(),
                write.lockUntil(),
                write.lockReason(),
                write.now()
        );
        userRiskProfileMapper.insertUserRiskScoreEvent(
                write.scoreEventId(),
                write.userId(),
                write.eventType(),
                write.scoreBefore(),
                write.scoreAfter() - write.scoreBefore(),
                write.scoreAfter(),
                write.riskLevelBefore(),
                write.riskLevelAfter(),
                write.lockReason(),
                write.ip(),
                write.deviceFingerprint(),
                write.metadataJson(),
                write.now()
        );

        long occurredAtMillis = write.now().toInstant().toEpochMilli();
        String eventId = "account-network-status-" + write.userId() + "-" + write.targetStatus() + "-" + occurredAtMillis;
        AccountStatusSyncMessage payload = new AccountStatusSyncMessage(
                eventId,
                write.userId(),
                write.targetStatus(),
                write.expectedStatus(),
                write.lockReason(),
                occurredAtMillis
        );
        outboxEvents.register(new OutboxEventRequest(
                eventId,
                AccountStatusSyncRouting.EVENT_TYPE,
                AccountStatusSyncRouting.AGGREGATE_TYPE,
                String.valueOf(write.userId()),
                AccountStatusSyncRouting.EXCHANGE,
                AccountStatusSyncRouting.ROUTING_KEY,
                payload,
                eventId
        ));
    }

    private void registerTerminatedEmailBloomSync(Long userId, String emailHash) {
        if (StrUtil.isBlank(emailHash)) {
            return;
        }
        AfterCommitExecutor.run(() -> {
            try {
                terminatedAccountEmailBloomService.addTerminatedEmailHashAsync(emailHash);
            } catch (Exception e) {
                log.warn("Terminated account email bloom sync failed, userId={}, reason={}",
                        userId, e.getMessage());
            }
        });
    }

    public record TerminationData(Long terminationId,
                                  String email,
                                  String emailHash,
                                  String phone,
                                  String phoneHash) {
    }

    public record NetworkLockWrite(Long userId,
                                   int currentEnvScore,
                                   int nextBehaviorScoreDelta,
                                   int scoreBefore,
                                   int scoreAfter,
                                   String riskLevelBefore,
                                   String riskLevelAfter,
                                   int nextLockCount,
                                   OffsetDateTime now,
                                   OffsetDateTime lockUntil,
                                   String lockReason,
                                   String eventType,
                                   Long scoreEventId,
                                   String ip,
                                   String deviceFingerprint,
                                   String metadataJson,
                                   String targetStatus,
                                   String expectedStatus,
                                   TerminationData termination) {
    }
}
