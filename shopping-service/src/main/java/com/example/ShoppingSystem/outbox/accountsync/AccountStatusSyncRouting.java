package com.example.ShoppingSystem.outbox.accountsync;

/**
 * RISK -> CORE 账号状态同步的 RabbitMQ 路由常量。
 * 生产端（风控）写 outbox_event 时使用 EXCHANGE/ROUTING_KEY，消费端（CORE）按同样常量绑定队列。
 */
public final class AccountStatusSyncRouting {

    private AccountStatusSyncRouting() {
    }

    public static final String EVENT_TYPE = "ACCOUNT_STATUS_SYNC";
    public static final String AGGREGATE_TYPE = "USER_LOGIN_IDENTITY";

    public static final String EXCHANGE = "account.status.sync.exchange";
    public static final String QUEUE = "account.status.sync.queue";
    public static final String DEAD_LETTER_QUEUE = "account.status.sync.dlq";

    public static final String ROUTING_KEY = "account.status.sync";
    public static final String DEAD_ROUTING_KEY = "account.status.sync.dead";
}