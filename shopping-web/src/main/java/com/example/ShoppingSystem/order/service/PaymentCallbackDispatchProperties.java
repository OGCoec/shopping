package com.example.ShoppingSystem.order.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment-callback.dispatch")
public class PaymentCallbackDispatchProperties {

    private boolean enabled = true;
    private int batchSize = 100;
    private int maxBatchesPerRun = 10;
    private int consumerBatchSize = 50;
    private int maxRetry = 3;
    private long retryBackoffBaseMillis = 5000L;
    private boolean dbScanEnabled = false;
    private long fallbackDbScanIntervalMillis = 300000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public int getConsumerBatchSize() {
        return consumerBatchSize;
    }

    public void setConsumerBatchSize(int consumerBatchSize) {
        this.consumerBatchSize = consumerBatchSize;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public long getRetryBackoffBaseMillis() {
        return retryBackoffBaseMillis;
    }

    public void setRetryBackoffBaseMillis(long retryBackoffBaseMillis) {
        this.retryBackoffBaseMillis = retryBackoffBaseMillis;
    }

    public boolean isDbScanEnabled() {
        return dbScanEnabled;
    }

    public void setDbScanEnabled(boolean dbScanEnabled) {
        this.dbScanEnabled = dbScanEnabled;
    }

    public long getFallbackDbScanIntervalMillis() {
        return fallbackDbScanIntervalMillis;
    }

    public void setFallbackDbScanIntervalMillis(long fallbackDbScanIntervalMillis) {
        this.fallbackDbScanIntervalMillis = fallbackDbScanIntervalMillis;
    }
}
