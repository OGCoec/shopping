package com.example.ShoppingSystem.outbox.accountsync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * RISK -> CORE 账号状态同步事件载荷。
 * 由风控本地事务通过 outbox_event 投递，CORE 消费端据此更新 user_login_identity.status。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountStatusSyncMessage {

    private int schemaVersion = 1;
    private String eventId;
    private Long userId;
    /** 目标状态，例如 RISK_TERMINATED / ACTIVE / LOCKED。 */
    private String targetStatus;
    /** 仅当为条件更新时使用的期望旧状态，为空表示无条件更新。 */
    private String expectedStatus;
    private String reason;
    private long occurredAtEpochMillis;
    private String loadtestFault;

    public AccountStatusSyncMessage() {
    }

    public AccountStatusSyncMessage(String eventId,
                                    Long userId,
                                    String targetStatus,
                                    String expectedStatus,
                                    String reason,
                                    long occurredAtEpochMillis) {
        this.eventId = eventId;
        this.userId = userId;
        this.targetStatus = targetStatus;
        this.expectedStatus = expectedStatus;
        this.reason = reason;
        this.occurredAtEpochMillis = occurredAtEpochMillis;
    }

    public String getLoadtestFault() {
        return loadtestFault;
    }

    public void setLoadtestFault(String loadtestFault) {
        this.loadtestFault = loadtestFault;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTargetStatus() {
        return targetStatus;
    }

    public void setTargetStatus(String targetStatus) {
        this.targetStatus = targetStatus;
    }

    public String getExpectedStatus() {
        return expectedStatus;
    }

    public void setExpectedStatus(String expectedStatus) {
        this.expectedStatus = expectedStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getOccurredAtEpochMillis() {
        return occurredAtEpochMillis;
    }

    public void setOccurredAtEpochMillis(long occurredAtEpochMillis) {
        this.occurredAtEpochMillis = occurredAtEpochMillis;
    }
}
