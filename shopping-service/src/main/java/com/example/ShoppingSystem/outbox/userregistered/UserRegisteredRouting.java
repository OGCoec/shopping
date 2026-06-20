package com.example.ShoppingSystem.outbox.userregistered;

/**
 * 注册成功 CORE -> RISK 最终一致的 RabbitMQ 路由常量。
 * 生产端（注册，CORE 本地事务）写 outbox_event 时使用 EXCHANGE/ROUTING_KEY，
 * 消费端（RISK）按同样常量绑定队列，补写风控档案与设备风控。
 */
public final class UserRegisteredRouting {

    private UserRegisteredRouting() {
    }

    public static final String EVENT_TYPE = "USER_REGISTERED";
    public static final String AGGREGATE_TYPE = "USER_RISK_PROFILE";

    public static final String EXCHANGE = "user.registered.exchange";
    public static final String QUEUE = "user.registered.queue";
    public static final String DEAD_LETTER_QUEUE = "user.registered.dlq";

    public static final String ROUTING_KEY = "user.registered";
    public static final String DEAD_ROUTING_KEY = "user.registered.dead";
}