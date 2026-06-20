package com.example.ShoppingSystem.security.userregistered;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.risk.RegisterRiskProfileMapper;
import com.example.ShoppingSystem.outbox.annotation.IdempotentConsumer;
import com.example.ShoppingSystem.outbox.userregistered.UserRegisteredMessage;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceRiskProfileWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * RISK 注册风控补写消费者。
 * 消费注册流程（CORE 本地事务 + outbox）投递的 UserRegisteredMessage，
 * 在 RISK 库补写 user_risk_profile 与设备风控；数据库级幂等由 @IdempotentConsumer 切面统一处理。
 */
@Component
public class UserRegisteredConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredConsumer.class);

    private final RegisterRiskProfileMapper registerRiskProfileMapper;
    private final DeviceRiskProfileWriteService deviceRiskProfileWriteService;

    public UserRegisteredConsumer(RegisterRiskProfileMapper registerRiskProfileMapper,
                                  DeviceRiskProfileWriteService deviceRiskProfileWriteService) {
        this.registerRiskProfileMapper = registerRiskProfileMapper;
        this.deviceRiskProfileWriteService = deviceRiskProfileWriteService;
    }

    @RabbitListener(
            queues = "#{userRegisteredQueue.name}",
            containerFactory = "userRegisteredRabbitListenerContainerFactory"
    )
    @IdempotentConsumer(route = DataSourceRoute.RISK, consumer = "user-registered-risk", eventId = "#message.eventId")
    public void consume(UserRegisteredMessage message) {
        if (!isUsable(message)) {
            log.warn("[UserRegistered] invalid message skipped, message={}", message);
            return;
        }
        OffsetDateTime occurredAt = OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(message.getOccurredAtEpochMillis()), ZoneOffset.UTC);
        registerRiskProfileMapper.upsertUserRiskProfile(
                message.getUserId(),
                message.getTotalScore(),
                message.getRiskLevel(),
                occurredAt,
                message.getRequestIp(),
                message.getDeviceFingerprint(),
                occurredAt
        );
        deviceRiskProfileWriteService.recordSuccess(
                message.getUserId(),
                message.getDeviceFingerprint(),
                message.getRequestIp(),
                "REGISTER_SUCCESS"
        );
        log.info("[UserRegistered] applied risk writes, userId={}, eventId={}",
                message.getUserId(), message.getEventId());
    }

    private boolean isUsable(UserRegisteredMessage message) {
        return message != null
                && message.getUserId() != null
                && message.getEventId() != null && !message.getEventId().isBlank();
    }
}