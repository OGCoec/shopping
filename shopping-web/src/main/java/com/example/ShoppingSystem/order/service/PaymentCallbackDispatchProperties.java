package com.example.ShoppingSystem.order.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment-callback.dispatch")
public class PaymentCallbackDispatchProperties {

    private boolean enabled = true;
    private int batchSize = 100;
    private int consumerBatchSize = 50;
    private int maxRetry = 3;
    private long retryBackoffBaseMillis = 5000L;

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
}
