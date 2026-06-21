package com.example.ShoppingSystem.outbox.orderstock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderStockDeductRequestedMessage {

    private int schemaVersion = 1;
    private String eventId;
    private String orderNo;
    private Long userId;
    private String skuIdHex;
    private String skuIdText;
    private Long spuId;
    private Long categoryId;
    private String skuCode;
    private String skuName;
    private String specJson;
    private String skuImageUrl;
    private String priceYuan;
    private boolean pointExchangeEnabled;
    private Long pointExchangePoints;
    private boolean hotSku;
    private int quantity;
    private String idempotencyKey;
    private String rawUserCouponId;
    private long createdAtEpochMillis;
    private long expireAtEpochMillis;
    private String inventoryType;
    private long occurredAtEpochMillis;
    private String loadtestFault;

    public OrderStockDeductRequestedMessage() {
    }

    public OrderStockDeductRequestedMessage(String eventId,
                                           String orderNo,
                                           Long userId,
                                           String skuIdHex,
                                           String skuIdText,
                                           Long spuId,
                                           Long categoryId,
                                           String skuCode,
                                           String skuName,
                                           String specJson,
                                           String skuImageUrl,
                                           String priceYuan,
                                           boolean pointExchangeEnabled,
                                           Long pointExchangePoints,
                                           boolean hotSku,
                                           int quantity,
                                           String idempotencyKey,
                                           String rawUserCouponId,
                                           long createdAtEpochMillis,
                                           long expireAtEpochMillis,
                                           String inventoryType,
                                           long occurredAtEpochMillis) {
        this.eventId = eventId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.skuIdHex = skuIdHex;
        this.skuIdText = skuIdText;
        this.spuId = spuId;
        this.categoryId = categoryId;
        this.skuCode = skuCode;
        this.skuName = skuName;
        this.specJson = specJson;
        this.skuImageUrl = skuImageUrl;
        this.priceYuan = priceYuan;
        this.pointExchangeEnabled = pointExchangeEnabled;
        this.pointExchangePoints = pointExchangePoints;
        this.hotSku = hotSku;
        this.quantity = quantity;
        this.idempotencyKey = idempotencyKey;
        this.rawUserCouponId = rawUserCouponId;
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.expireAtEpochMillis = expireAtEpochMillis;
        this.inventoryType = inventoryType;
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

    public String getSkuIdHex() {
        return skuIdHex;
    }

    public void setSkuIdHex(String skuIdHex) {
        this.skuIdHex = skuIdHex;
    }

    public String getSkuIdText() {
        return skuIdText;
    }

    public void setSkuIdText(String skuIdText) {
        this.skuIdText = skuIdText;
    }

    public Long getSpuId() {
        return spuId;
    }

    public void setSpuId(Long spuId) {
        this.spuId = spuId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getSkuName() {
        return skuName;
    }

    public void setSkuName(String skuName) {
        this.skuName = skuName;
    }

    public String getSpecJson() {
        return specJson;
    }

    public void setSpecJson(String specJson) {
        this.specJson = specJson;
    }

    public String getSkuImageUrl() {
        return skuImageUrl;
    }

    public void setSkuImageUrl(String skuImageUrl) {
        this.skuImageUrl = skuImageUrl;
    }

    public String getPriceYuan() {
        return priceYuan;
    }

    public void setPriceYuan(String priceYuan) {
        this.priceYuan = priceYuan;
    }

    public boolean isPointExchangeEnabled() {
        return pointExchangeEnabled;
    }

    public void setPointExchangeEnabled(boolean pointExchangeEnabled) {
        this.pointExchangeEnabled = pointExchangeEnabled;
    }

    public Long getPointExchangePoints() {
        return pointExchangePoints;
    }

    public void setPointExchangePoints(Long pointExchangePoints) {
        this.pointExchangePoints = pointExchangePoints;
    }

    public boolean isHotSku() {
        return hotSku;
    }

    public void setHotSku(boolean hotSku) {
        this.hotSku = hotSku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRawUserCouponId() {
        return rawUserCouponId;
    }

    public void setRawUserCouponId(String rawUserCouponId) {
        this.rawUserCouponId = rawUserCouponId;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public void setCreatedAtEpochMillis(long createdAtEpochMillis) {
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    public long getExpireAtEpochMillis() {
        return expireAtEpochMillis;
    }

    public void setExpireAtEpochMillis(long expireAtEpochMillis) {
        this.expireAtEpochMillis = expireAtEpochMillis;
    }

    public String getInventoryType() {
        return inventoryType;
    }

    public void setInventoryType(String inventoryType) {
        this.inventoryType = inventoryType;
    }

    public long getOccurredAtEpochMillis() {
        return occurredAtEpochMillis;
    }

    public void setOccurredAtEpochMillis(long occurredAtEpochMillis) {
        this.occurredAtEpochMillis = occurredAtEpochMillis;
    }
}
