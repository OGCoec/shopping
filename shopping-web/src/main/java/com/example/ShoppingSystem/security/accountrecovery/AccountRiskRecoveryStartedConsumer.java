package com.example.ShoppingSystem.security.accountrecovery;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper;
import com.example.ShoppingSystem.outbox.accountrecovery.AccountRiskRecoveryStartedMessage;
import com.example.ShoppingSystem.outbox.annotation.IdempotentConsumer;
import com.example.ShoppingSystem.outbox.fault.FaultInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class AccountRiskRecoveryStartedConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountRiskRecoveryStartedConsumer.class);

    private final UserRiskProfileMapper userRiskProfileMapper;
    private final FaultInjector faultInjector;

    public AccountRiskRecoveryStartedConsumer(UserRiskProfileMapper userRiskProfileMapper,
                                              FaultInjector faultInjector) {
        this.userRiskProfileMapper = userRiskProfileMapper;
        this.faultInjector = faultInjector;
    }

    @RabbitListener(
            queues = "#{accountRiskRecoveryStartedQueue.name}",
            containerFactory = "accountRiskRecoveryStartedRabbitListenerContainerFactory"
    )
    @IdempotentConsumer(route = DataSourceRoute.RISK, consumer = "account-risk-recovery-started-risk",
            eventId = "#message.eventId", transactional = true)
    public void consume(AccountRiskRecoveryStartedMessage message) {
        if (!isUsable(message)) {
            log.warn("[AccountRiskRecoveryStarted] invalid message skipped, message={}", message);
            return;
        }
        faultInjector.maybeFail("account-risk-recovery-started-risk", message.getLoadtestFault());
        int updated = userRiskProfileMapper.markRiskRecoveryStarted(
                message.getUserId(),
                startedAt(message.getStartedAtEpochMillis())
        );
        log.info("[AccountRiskRecoveryStarted] applied, userId={}, updated={}, eventId={}",
                message.getUserId(), updated, message.getEventId());
    }

    private OffsetDateTime startedAt(long epochMillis) {
        long millis = epochMillis <= 0L ? System.currentTimeMillis() : epochMillis;
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }

    private boolean isUsable(AccountRiskRecoveryStartedMessage message) {
        return message != null
                && message.getUserId() != null
                && message.getEventId() != null && !message.getEventId().isBlank();
    }
}