package com.example.ShoppingSystem.outbox.accountrecovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountRiskRecoveryStartedMessage {

    private int schemaVersion = 1;
    private String eventId;
    private Long userId;
    private long startedAtEpochMillis;
    private String loadtestFault;

    public AccountRiskRecoveryStartedMessage() {
    }

    public AccountRiskRecoveryStartedMessage(String eventId,
                                             Long userId,
                                             long startedAtEpochMillis) {
        this.eventId = eventId;
        this.userId = userId;
        this.startedAtEpochMillis = startedAtEpochMillis;
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

    public long getStartedAtEpochMillis() {
        return startedAtEpochMillis;
    }

    public void setStartedAtEpochMillis(long startedAtEpochMillis) {
        this.startedAtEpochMillis = startedAtEpochMillis;
    }

    public String getLoadtestFault() {
        return loadtestFault;
    }

    public void setLoadtestFault(String loadtestFault) {
        this.loadtestFault = loadtestFault;
    }
}