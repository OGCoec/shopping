package com.example.ShoppingSystem.outbox.passwordresetsync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 找回密码成功事件载荷。
 * 由找回密码流程在 CORE 本地事务内通过 outbox_event 投递，
 * RISK 消费端据此补写设备风控成功记录（审计性质，最终一致）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PasswordResetSucceededMessage {

    private int schemaVersion = 1;
    private String eventId;
    private Long userId;
    private String deviceFingerprint;
    private String clientIp;
    private String scene;
    private long occurredAtEpochMillis;

    public PasswordResetSucceededMessage() {
    }

    public PasswordResetSucceededMessage(String eventId,
                                         Long userId,
                                         String deviceFingerprint,
                                         String clientIp,
                                         String scene,
                                         long occurredAtEpochMillis) {
        this.eventId = eventId;
        this.userId = userId;
        this.deviceFingerprint = deviceFingerprint;
        this.clientIp = clientIp;
        this.scene = scene;
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

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public long getOccurredAtEpochMillis() {
        return occurredAtEpochMillis;
    }

    public void setOccurredAtEpochMillis(long occurredAtEpochMillis) {
        this.occurredAtEpochMillis = occurredAtEpochMillis;
    }
}