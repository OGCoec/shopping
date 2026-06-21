package com.example.ShoppingSystem.service.user.auth.risk.impl.UserAuthFailureRiskService;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.user.UserLoginIdentityMapper;
import com.example.ShoppingSystem.outbox.OutboxEventRequest;
import com.example.ShoppingSystem.outbox.accountrecovery.AccountRiskRecoveryStartedMessage;
import com.example.ShoppingSystem.outbox.accountrecovery.AccountRiskRecoveryStartedRouting;
import com.example.ShoppingSystem.outbox.annotation.OutboxEventCollector;
import com.example.ShoppingSystem.outbox.annotation.TransactionalOutbox;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class AuthLockRecoveryCoreWriter {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LOCKED = "LOCKED";

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final OutboxEventCollector outboxEvents;

    public AuthLockRecoveryCoreWriter(UserLoginIdentityMapper userLoginIdentityMapper,
                                      OutboxEventCollector outboxEvents) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.outboxEvents = outboxEvents;
    }

    @TransactionalOutbox(DataSourceRoute.CORE)
    public boolean activateAndEnqueueRiskRecovery(Long userId, OffsetDateTime startedAt) {
        int activated = userLoginIdentityMapper.updateStatusByUserIdIfStatus(userId, STATUS_LOCKED, STATUS_ACTIVE);
        if (activated <= 0) {
            return false;
        }
        OffsetDateTime occurredAt = startedAt == null ? OffsetDateTime.now() : startedAt;
        long occurredAtMillis = occurredAt.toInstant().toEpochMilli();
        String eventId = "auth-lock-recovery-started:" + userId + ":" + occurredAtMillis;
        AccountRiskRecoveryStartedMessage payload = new AccountRiskRecoveryStartedMessage(
                eventId,
                userId,
                occurredAtMillis
        );
        outboxEvents.register(new OutboxEventRequest(
                eventId,
                AccountRiskRecoveryStartedRouting.EVENT_TYPE,
                AccountRiskRecoveryStartedRouting.AGGREGATE_TYPE,
                String.valueOf(userId),
                AccountRiskRecoveryStartedRouting.EXCHANGE,
                AccountRiskRecoveryStartedRouting.ROUTING_KEY,
                payload,
                eventId
        ));
        return true;
    }
}