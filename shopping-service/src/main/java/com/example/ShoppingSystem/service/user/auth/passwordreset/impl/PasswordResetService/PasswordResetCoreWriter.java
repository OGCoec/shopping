package com.example.ShoppingSystem.service.user.auth.passwordreset.impl.PasswordResetService;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.user.UserLoginIdentityMapper;
import com.example.ShoppingSystem.outbox.OutboxEventRequest;
import com.example.ShoppingSystem.outbox.annotation.OutboxEventCollector;
import com.example.ShoppingSystem.outbox.annotation.TransactionalOutbox;
import com.example.ShoppingSystem.outbox.passwordresetsync.PasswordResetSucceededMessage;
import com.example.ShoppingSystem.outbox.passwordresetsync.PasswordResetSucceededRouting;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 找回密码 CORE 段写入器。
 * 单独成 Bean 以便 @TransactionalOutbox 切面生效（避免同类自调用绕过 AOP），
 * 方法体只触及 CORE 库：写新密码，并登记找回密码成功事件，
 * 由切面在同一 CORE 本地事务内写 outbox_event 一起提交。
 * RISK 库设备风控成功记录由 PasswordResetSucceededConsumer 幂等补写，最终一致。
 */
@Component
public class PasswordResetCoreWriter {

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventCollector outboxEvents;

    public PasswordResetCoreWriter(UserLoginIdentityMapper userLoginIdentityMapper,
                                   PasswordEncoder passwordEncoder,
                                   OutboxEventCollector outboxEvents) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.passwordEncoder = passwordEncoder;
        this.outboxEvents = outboxEvents;
    }

    /**
     * @return true 表示密码已更新且事件已登记；false 表示目标账号无更新（事务内不登记事件）。
     */
    @TransactionalOutbox(DataSourceRoute.CORE)
    public boolean updatePasswordAndEnqueue(Long userId, String rawPassword, PasswordResetSucceededMessage event) {
        int updated = userLoginIdentityMapper.updateEmailPasswordHashByUserId(
                userId,
                passwordEncoder.encode(rawPassword),
                IdUtil.fastSimpleUUID().substring(0, 24));
        if (updated <= 0) {
            return false;
        }
        outboxEvents.register(new OutboxEventRequest(
                event.getEventId(),
                PasswordResetSucceededRouting.EVENT_TYPE,
                PasswordResetSucceededRouting.AGGREGATE_TYPE,
                String.valueOf(event.getUserId()),
                PasswordResetSucceededRouting.EXCHANGE,
                PasswordResetSucceededRouting.ROUTING_KEY,
                event,
                event.getEventId()
        ));
        return true;
    }
}