package com.example.ShoppingSystem.outbox.orderinventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderInventoryReleaseRequestedMessage {

    private int schemaVersion = 1;
    private String eventId;
    private String orderNo;
    private Long userId;
    private String reason;
    private Map<String, Object> order;
    private List<Map<String, Object>> items;
    private long occurredAtEpochMillis;
    private String loadtestFault;

    public OrderInventoryReleaseRequestedMessage() {
    }

    public OrderInventoryReleaseRequestedMessage(String eventId,
                                                 String orderNo,
                                                 Long userId,
                                                 String reason,
                                                 Map<String, Object> order,
                                                 List<Map<String, Object>> items,
                                                 long occurredAtEpochMillis) {
        this.eventId = eventId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.reason = reason;
        this.order = order;
        this.items = items;
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

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Object> getOrder() {
        return order;
    }

    public void setOrder(Map<String, Object> order) {
        this.order = order;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public void setItems(List<Map<String, Object>> items) {
        this.items = items;
    }

    public long getOccurredAtEpochMillis() {
        return occurredAtEpochMillis;
    }

    public void setOccurredAtEpochMillis(long occurredAtEpochMillis) {
        this.occurredAtEpochMillis = occurredAtEpochMillis;
    }
}
