package com.example.ShoppingSystem.order.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment-callback.stream")
public class PaymentCallbackStreamProperties {

    private boolean enabled = true;
    private String key = "shopping:payment:callback:stream";
    private String group = "payment-callback-flusher";
    private String consumerName;
    private long flushIntervalMillis = 5000L;
    private int batchSize = 100;
    private int maxBatchesPerRun = 10;
    private long pendingIdleTimeoutMs = 60000L;
    private long processingTimeoutMs = 120000L;
    private long dedupeTtlHours = 72L;
    private long receivedOrderMarkerTtlMillis = 600000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public long getFlushIntervalMillis() {
        return flushIntervalMillis;
    }

    public void setFlushIntervalMillis(long flushIntervalMillis) {
        this.flushIntervalMillis = flushIntervalMillis;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxBatchesPerRun() {
        return maxBatchesPerRun;
    }

    public void setMaxBatchesPerRun(int maxBatchesPerRun) {
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    public long getPendingIdleTimeoutMs() {
        return pendingIdleTimeoutMs;
    }

    public void setPendingIdleTimeoutMs(long pendingIdleTimeoutMs) {
        this.pendingIdleTimeoutMs = pendingIdleTimeoutMs;
    }

    public long getProcessingTimeoutMs() {
        return processingTimeoutMs;
    }

    public void setProcessingTimeoutMs(long processingTimeoutMs) {
        this.processingTimeoutMs = processingTimeoutMs;
    }

    public long getDedupeTtlHours() {
        return dedupeTtlHours;
    }

    public void setDedupeTtlHours(long dedupeTtlHours) {
        this.dedupeTtlHours = dedupeTtlHours;
    }

    public long getReceivedOrderMarkerTtlMillis() {
        return receivedOrderMarkerTtlMillis;
    }

    public void setReceivedOrderMarkerTtlMillis(long receivedOrderMarkerTtlMillis) {
        this.receivedOrderMarkerTtlMillis = receivedOrderMarkerTtlMillis;
    }
}
