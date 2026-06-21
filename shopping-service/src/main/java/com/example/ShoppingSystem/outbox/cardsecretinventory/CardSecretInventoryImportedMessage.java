package com.example.ShoppingSystem.outbox.cardsecretinventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CardSecretInventoryImportedMessage {

    private int schemaVersion = 1;
    private String eventId;
    private Long spuId;
    private String skuIdHex;
    private int insertedCount;
    private String batchNo;
    private String batchFingerprint;
    private long occurredAtEpochMillis;
    private String loadtestFault;

    public CardSecretInventoryImportedMessage() {
    }

    public CardSecretInventoryImportedMessage(String eventId,
                                              Long spuId,
                                              String skuIdHex,
                                              int insertedCount,
                                              String batchNo,
                                              String batchFingerprint,
                                              long occurredAtEpochMillis) {
        this.eventId = eventId;
        this.spuId = spuId;
        this.skuIdHex = skuIdHex;
        this.insertedCount = insertedCount;
        this.batchNo = batchNo;
        this.batchFingerprint = batchFingerprint;
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

    public Long getSpuId() {
        return spuId;
    }

    public void setSpuId(Long spuId) {
        this.spuId = spuId;
    }

    public String getSkuIdHex() {
        return skuIdHex;
    }

    public void setSkuIdHex(String skuIdHex) {
        this.skuIdHex = skuIdHex;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    public void setInsertedCount(int insertedCount) {
        this.insertedCount = insertedCount;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getBatchFingerprint() {
        return batchFingerprint;
    }

    public void setBatchFingerprint(String batchFingerprint) {
        this.batchFingerprint = batchFingerprint;
    }

    public long getOccurredAtEpochMillis() {
        return occurredAtEpochMillis;
    }

    public void setOccurredAtEpochMillis(long occurredAtEpochMillis) {
        this.occurredAtEpochMillis = occurredAtEpochMillis;
    }
}
