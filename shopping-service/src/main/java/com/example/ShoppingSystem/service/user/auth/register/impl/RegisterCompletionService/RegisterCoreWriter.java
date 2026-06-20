package com.example.ShoppingSystem.service.user.auth.register.impl.RegisterCompletionService;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.user.UserLoginIdentityMapper;
import com.example.ShoppingSystem.outbox.OutboxEventRequest;
import com.example.ShoppingSystem.outbox.annotation.OutboxEventCollector;
import com.example.ShoppingSystem.outbox.annotation.TransactionalOutbox;
import com.example.ShoppingSystem.outbox.userregistered.UserRegisteredMessage;
import com.example.ShoppingSystem.outbox.userregistered.UserRegisteredRouting;
import com.example.ShoppingSystem.service.user.auth.login.UserProfileService;
import com.example.ShoppingSystem.service.user.auth.login.model.UserProfileDraft;
import org.springframework.stereotype.Component;

/**
 * 注册 CORE 段写入器。
 * 单独成 Bean 以便 @TransactionalOutbox 切面生效（避免同类自调用绕过 AOP），
 * 方法体只触及 CORE 库：写身份、初始化档案，并登记注册成功事件，
 * 由切面在同一 CORE 本地事务内写 outbox_event 一起提交。
 */
@Component
public class RegisterCoreWriter {

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final UserProfileService userProfileService;
    private final OutboxEventCollector outboxEvents;

    public RegisterCoreWriter(UserLoginIdentityMapper userLoginIdentityMapper,
                              UserProfileService userProfileService,
                              OutboxEventCollector outboxEvents) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.userProfileService = userProfileService;
        this.outboxEvents = outboxEvents;
    }

    @TransactionalOutbox(DataSourceRoute.CORE)
    public void writeIdentityAndEnqueue(UserLoginIdentity identity,
                                        String username,
                                        UserRegisteredMessage event) {
        userLoginIdentityMapper.insertEmailIdentity(identity);
        userProfileService.initIfAbsent(identity.getUserId(), UserProfileDraft.builder()
                .username(username)
                .build());
        outboxEvents.register(new OutboxEventRequest(
                event.getEventId(),
                UserRegisteredRouting.EVENT_TYPE,
                UserRegisteredRouting.AGGREGATE_TYPE,
                String.valueOf(event.getUserId()),
                UserRegisteredRouting.EXCHANGE,
                UserRegisteredRouting.ROUTING_KEY,
                event,
                event.getEventId()
        ));
    }
}