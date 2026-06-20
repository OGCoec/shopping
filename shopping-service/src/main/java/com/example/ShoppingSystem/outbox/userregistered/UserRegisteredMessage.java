package com.example.ShoppingSystem.outbox.userregistered;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 注册成功事件载荷。
 * 由注册流程在 CORE 本地事务内通过 outbox_event 投递，
 * RISK 消费端据此补写 user_risk_profile 与设备风控。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRegisteredMessage {

    private int schemaVersion = 1;
    private String eventId;
    private Long userId;
    private int totalScore;
    private String riskLevel;
    private String deviceFingerprint;
    private String requestIp;
    private long occurredAtEpochMillis;

    public UserRegisteredMessage() {
    }

    public UserRegisteredMessage(String eventId,
                                 Long userId,
                                 int totalScore,
                                 String riskLevel,
                                 String deviceFingerprint,
                                 String requestIp,
                                 long occurredAtEpochMillis) {
        this.eventId = eventId;
        this.userId = userId;
        this.totalScore = totalScore;
        this.riskLevel = riskLevel;
        this.deviceFingerprint = deviceFingerprint;
        this.requestIp = requestIp;
        this.occurredAtEpochMillis = occurredAtEpochMillis;
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

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }

    public long getOccurredAtEpochMillis() {
        return occurredAtEpochMillis;
    }

    public void setOccurredAtEpochMillis(long occurredAtEpochMillis) {
        this.occurredAtEpochMillis = occurredAtEpochMillis;
    }
}