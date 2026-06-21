package com.example.ShoppingSystem.security.accountsync;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.user.UserLoginIdentityMapper;
import com.example.ShoppingSystem.outbox.annotation.IdempotentConsumer;
import com.example.ShoppingSystem.outbox.accountsync.AccountStatusSyncMessage;
import com.example.ShoppingSystem.outbox.fault.FaultInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AccountStatusSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusSyncConsumer.class);

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final FaultInjector faultInjector;

    public AccountStatusSyncConsumer(UserLoginIdentityMapper userLoginIdentityMapper,
                                     FaultInjector faultInjector) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.faultInjector = faultInjector;
    }

    @RabbitListener(
            queues = "#{accountStatusSyncQueue.name}",
            containerFactory = "accountStatusSyncRabbitListenerContainerFactory"
    )
    @IdempotentConsumer(route = DataSourceRoute.CORE, consumer = "account-status-sync-core",
            eventId = "#message.eventId", transactional = true)
    public void consume(AccountStatusSyncMessage message) {
        if (!isUsable(message)) {
            log.warn("[AccountStatusSync] invalid message skipped, message={}", message);
            return;
        }
        faultInjector.maybeFail("account-status-sync-core", message.getLoadtestFault());
        String expected = normalize(message.getExpectedStatus());
        int updated;
        if (expected == null) {
            updated = userLoginIdentityMapper.updateStatusByUserId(
                    message.getUserId(), message.getTargetStatus().trim());
        } else {
            updated = userLoginIdentityMapper.updateStatusByUserIdIfStatus(
                    message.getUserId(), expected, message.getTargetStatus().trim());
        }
        log.info("[AccountStatusSync] applied, userId={}, targetStatus={}, expectedStatus={}, updated={}, eventId={}",
                message.getUserId(), message.getTargetStatus(), expected, updated, message.getEventId());
    }

    private boolean isUsable(AccountStatusSyncMessage message) {
        return message != null
                && message.getUserId() != null
                && message.getEventId() != null && !message.getEventId().isBlank()
                && message.getTargetStatus() != null && !message.getTargetStatus().isBlank();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}