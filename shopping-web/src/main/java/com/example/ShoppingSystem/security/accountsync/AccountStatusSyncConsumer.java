package com.example.ShoppingSystem.security.accountsync;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.user.UserLoginIdentityMapper;
import com.example.ShoppingSystem.outbox.annotation.IdempotentConsumer;
import com.example.ShoppingSystem.outbox.accountsync.AccountStatusSyncMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * CORE 账号状态同步消费者。
 * 消费 RISK 投递的 AccountStatusSyncMessage，在 CORE 库更新 user_login_identity.status；
 * 数据库级幂等由 @IdempotentConsumer 切面统一处理，重复事件只生效一次。
 */
@Component
public class AccountStatusSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusSyncConsumer.class);

    private final UserLoginIdentityMapper userLoginIdentityMapper;

    public AccountStatusSyncConsumer(UserLoginIdentityMapper userLoginIdentityMapper) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
    }

    @RabbitListener(
            queues = "#{accountStatusSyncQueue.name}",
            containerFactory = "accountStatusSyncRabbitListenerContainerFactory"
    )
    @IdempotentConsumer(route = DataSourceRoute.CORE, consumer = "account-status-sync-core", eventId = "#message.eventId")
    public void consume(AccountStatusSyncMessage message) {
        if (!isUsable(message)) {
            log.warn("[AccountStatusSync] invalid message skipped, message={}", message);
            return;
        }
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