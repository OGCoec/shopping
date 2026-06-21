package com.example.ShoppingSystem.outbox.couponexpire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CouponTemplatesExpiredMessage {

    private int schemaVersion = 1;
    private String eventId;
    private List<String> templateIdHexes;
    private long occurredAtEpochMillis;
    private String loadtestFault;

    public CouponTemplatesExpiredMessage() {
    }

    public CouponTemplatesExpiredMessage(String eventId,
                                         List<String> templateIdHexes,
                                         long occurredAtEpochMillis) {
        this.eventId = eventId;
        this.templateIdHexes = templateIdHexes;
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

    public List<String> getTemplateIdHexes() {
        return templateIdHexes;
    }

    public void setTemplateIdHexes(List<String> templateIdHexes) {
        this.templateIdHexes = templateIdHexes;
    }

    public long getOccurredAtEpochMillis() {
        return occurredAtEpochMillis;
    }

    public void setOccurredAtEpochMillis(long occurredAtEpochMillis) {
        this.occurredAtEpochMillis = occurredAtEpochMillis;
    }
}
