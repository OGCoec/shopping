package com.example.ShoppingSystem.outbox;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "shopping.outbox.dispatcher")
public class OutboxDispatcherProperties {

    private boolean enabled = false;
    private int batchSize = 50;
    private int maxRetry = 10;
    private long retryBackoffBaseMs = 30_000L;
    private long processingTimeoutMs = 60_000L;
    private long confirmTimeoutMs = 5_000L;
    private List<DataSourceRoute> routes = new ArrayList<>(List.of(
            DataSourceRoute.RISK,
            DataSourceRoute.TRADE,
            DataSourceRoute.PRODUCT,
            DataSourceRoute.COUPON,
            DataSourceRoute.CORE
    ));

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

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public long getRetryBackoffBaseMs() {
        return retryBackoffBaseMs;
    }

    public void setRetryBackoffBaseMs(long retryBackoffBaseMs) {
        this.retryBackoffBaseMs = retryBackoffBaseMs;
    }

    public long getProcessingTimeoutMs() {
        return processingTimeoutMs;
    }

    public void setProcessingTimeoutMs(long processingTimeoutMs) {
        this.processingTimeoutMs = processingTimeoutMs;
    }

    public long getConfirmTimeoutMs() {
        return confirmTimeoutMs;
    }

    public void setConfirmTimeoutMs(long confirmTimeoutMs) {
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    public List<DataSourceRoute> getRoutes() {
        return routes;
    }

    public void setRoutes(List<DataSourceRoute> routes) {
        this.routes = routes == null ? new ArrayList<>() : new ArrayList<>(routes);
    }
}
