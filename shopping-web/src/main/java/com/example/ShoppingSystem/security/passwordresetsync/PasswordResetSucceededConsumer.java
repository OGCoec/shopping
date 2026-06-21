package com.example.ShoppingSystem.security.passwordresetsync;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.outbox.annotation.IdempotentConsumer;
import com.example.ShoppingSystem.outbox.passwordresetsync.PasswordResetSucceededMessage;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceRiskProfileWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RISK 找回密码设备风控补写消费者。
 * 消费找回密码流程（CORE 本地事务 + outbox）投递的 PasswordResetSucceededMessage，
 * 在 RISK 库补写设备风控成功记录；数据库级幂等由 @IdempotentConsumer 切面统一处理。
 */
@Component
public class PasswordResetSucceededConsumer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetSucceededConsumer.class);

    private final DeviceRiskProfileWriteService deviceRiskProfileWriteService;

    public PasswordResetSucceededConsumer(DeviceRiskProfileWriteService deviceRiskProfileWriteService) {
        this.deviceRiskProfileWriteService = deviceRiskProfileWriteService;
    }

    @RabbitListener(
            queues = "#{passwordResetSucceededQueue.name}",
            containerFactory = "passwordResetSucceededRabbitListenerContainerFactory"
    )
    @IdempotentConsumer(route = DataSourceRoute.RISK, consumer = "password-reset-succeeded-risk", eventId = "#message.eventId", transactional = true)
    public void consume(PasswordResetSucceededMessage message) {
        if (!isUsable(message)) {
            log.warn("[PasswordResetSucceeded] invalid message skipped, message={}", message);
            return;
        }
        deviceRiskProfileWriteService.recordSuccess(
                message.getUserId(),
                message.getDeviceFingerprint(),
                message.getClientIp(),
                scene(message));
        log.info("[PasswordResetSucceeded] applied risk write, userId={}, eventId={}",
                message.getUserId(), message.getEventId());
    }

    private String scene(PasswordResetSucceededMessage message) {
        String scene = message.getScene();
        return (scene == null || scene.isBlank()) ? "PASSWORD_RESET_SUCCESS" : scene.trim();
    }

    private boolean isUsable(PasswordResetSucceededMessage message) {
        return message != null
                && message.getUserId() != null
                && message.getEventId() != null && !message.getEventId().isBlank();
    }
}
