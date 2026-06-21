package com.example.ShoppingSystem.outbox.accountrecovery;

public final class AccountRiskRecoveryStartedRouting {

    private AccountRiskRecoveryStartedRouting() {
    }

    public static final String EVENT_TYPE = "ACCOUNT_RISK_RECOVERY_STARTED";
    public static final String AGGREGATE_TYPE = "USER_RISK_PROFILE";

    public static final String EXCHANGE = "account.risk.recovery.started.exchange";
    public static final String QUEUE = "account.risk.recovery.started.queue";
    public static final String DEAD_LETTER_QUEUE = "account.risk.recovery.started.dlq";

    public static final String ROUTING_KEY = "account.risk.recovery.started";
    public static final String DEAD_ROUTING_KEY = "account.risk.recovery.started.dead";
}