package com.example.ShoppingSystem.outbox.passwordresetsync;

/**
 * 找回密码成功 CORE -> RISK 最终一致的 RabbitMQ 路由常量。
 * 生产端（找回密码，CORE 本地事务）写 outbox_event 时使用 EXCHANGE/ROUTING_KEY，
 * 消费端（RISK）按同样常量绑定队列，补写设备风控成功记录。
 */
public final class PasswordResetSucceededRouting {

    private PasswordResetSucceededRouting() {
    }

    public static final String EVENT_TYPE = "PASSWORD_RESET_SUCCEEDED";
    public static final String AGGREGATE_TYPE = "DEVICE_RISK_PROFILE";

    public static final String EXCHANGE = "password.reset.succeeded.exchange";
    public static final String QUEUE = "password.reset.succeeded.queue";
    public static final String DEAD_LETTER_QUEUE = "password.reset.succeeded.dlq";

    public static final String ROUTING_KEY = "password.reset.succeeded";
    public static final String DEAD_ROUTING_KEY = "password.reset.succeeded.dead";
}