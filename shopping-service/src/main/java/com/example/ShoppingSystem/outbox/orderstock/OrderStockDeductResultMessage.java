package com.example.ShoppingSystem.outbox.orderstock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderStockDeductResultMessage {

    private int schemaVersion = 1;
    private String eventId;
    private String orderNo;
    private Long userId;
    private boolean success;
    private String code;
    private String message;
    private Integer remainingQuantity;
    private long occurredAtEpochMillis;

    public OrderStockDeductResultMessage() {
    }

    public OrderStockDeductResultMessage(String eventId,
                                         String orderNo,
                                         Long userId,
                                         boolean success,
                                         String code,
                                         String message,
                                         Integer remainingQuantity,
                                         long occurredAtEpochMillis) {
        this.eventId = eventId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.success = success;
        this.code = code;
        this.message = message;
        this.remainingQuantity = remainingQuantity;
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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(Integer remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    public long getOccurredAtEpochMillis() {
        return occurredAtEpochMillis;
    }

    public void setOccurredAtEpochMillis(long occurredAtEpochMillis) {
        this.occurredAtEpochMillis = occurredAtEpochMillis;
    }
}
