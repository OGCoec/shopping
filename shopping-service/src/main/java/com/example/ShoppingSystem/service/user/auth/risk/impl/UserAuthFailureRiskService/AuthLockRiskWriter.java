package com.example.ShoppingSystem.service.user.auth.risk.impl.UserAuthFailureRiskService;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.risk.UserRiskAccountTerminationMapper;
import com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper;
import com.example.ShoppingSystem.outbox.OutboxEventRequest;
import com.example.ShoppingSystem.outbox.accountsync.AccountStatusSyncMessage;
import com.example.ShoppingSystem.outbox.accountsync.AccountStatusSyncRouting;
import com.example.ShoppingSystem.outbox.annotation.OutboxEventCollector;
import com.example.ShoppingSystem.outbox.annotation.TransactionalOutbox;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 风控封号 RISK 段写入器。
 * 单独成 Bean 以便 @TransactionalOutbox 切面生效（避免同类自调用绕过 AOP），
 * 方法体只触及 RISK 库：写锁定/评分状态、可选写终止记录，并登记账号状态同步事件，
 * 由切面在同一 RISK 本地事务内写 outbox_event 一起提交。
 * CORE 库 user_login_identity.status 由 AccountStatusSyncConsumer 消费事件后幂等更新，最终一致。
 */
@Component
public class AuthLockRiskWriter {

    private final UserRiskProfileMapper userRiskProfileMapper;
    private final UserRiskAccountTerminationMapper userRiskAccountTerminationMapper;
    private final OutboxEventCollector outboxEvents;

    public AuthLockRiskWriter(UserRiskProfileMapper userRiskProfileMapper,
                              UserRiskAccountTerminationMapper userRiskAccountTerminationMapper,
                              OutboxEventCollector outboxEvents) {
        this.userRiskProfileMapper = userRiskProfileMapper;
        this.userRiskAccountTerminationMapper = userRiskAccountTerminationMapper;
        this.outboxEvents = outboxEvents;
    }

    /**
     * 在单个 RISK 本地事务内执行封号风控写入并登记 CORE 状态同步事件。
     * termination 非空表示终止分支，需要额外写 user_risk_account_termination。
     */
    @TransactionalOutbox(DataSourceRoute.RISK)
    public void applyLockAndEnqueue(AuthLockWrite write) {
        if (write.termination() != null) {
            TerminationData t = write.termination();
            userRiskAccountTerminationMapper.upsertRiskTermination(
                    t.terminationId(),
                    write.userId(),
                    t.email(),
                    t.emailHash(),
                    t.phone(),
                    t.phoneHash(),
                    write.lockReason(),
                    write.now(),
                    write.now()
            );
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
        String eventId = "acct-status-" + write.userId() + "-" + write.targetStatus() + "-" + occurredAtMillis;
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

    /** 终止分支需要的 CORE 身份派生数据，由调用方在 RISK 事务外预先查好。 */
    public record TerminationData(Long terminationId,
                                  String email,
                                  String emailHash,
                                  String phone,
                                  String phoneHash) {
    }

    /** 封号 RISK 写入与事件登记所需的全部参数。 */
    public record AuthLockWrite(Long userId,
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