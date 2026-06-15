package com.example.ShoppingSystem.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentCallbackDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackDispatchScheduler.class);

    private final PaymentCallbackDispatchService paymentCallbackDispatchService;
    private final PaymentCallbackDispatchProperties properties;

    public PaymentCallbackDispatchScheduler(PaymentCallbackDispatchService paymentCallbackDispatchService,
                                            PaymentCallbackDispatchProperties properties) {
        this.paymentCallbackDispatchService = paymentCallbackDispatchService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.payment-callback.dispatch.fallback-db-scan-interval-millis:300000}")
    public void dispatchPendingCallbacks() {
        if (!properties.isEnabled() || !properties.isDbScanEnabled()) {
            return;
        }
        int effectiveBatchSize = effectiveBatchSize();
        int maxBatches = maxBatchesPerRun();
        int batchCount = 0;
        int totalClaimed = 0;
        int totalWritten = 0;
        int totalRefunds = 0;
        int totalFailed = 0;
        boolean batchFailed = false;
        while (batchCount < maxBatches) {
            PaymentCallbackDispatchService.DispatchSummary summary;
            try {
                summary = paymentCallbackDispatchService.dispatchAvailable(properties.getBatchSize());
            } catch (Exception e) {
                batchFailed = true;
                log.warn("[PaymentCallback] fallback DB dispatch batch failed, batch={}, batchSize={}, totalClaimed={}, error={}",
                        batchCount + 1, effectiveBatchSize, totalClaimed, e.getMessage());
                break;
            }
            if (summary.claimedCount() <= 0) {
                break;
            }
            batchCount++;
            totalClaimed += summary.claimedCount();
            totalWritten += summary.inboxWrittenCount();
            totalRefunds += summary.refundCount();
            totalFailed += summary.failedCount();
            if (summary.claimedCount() < effectiveBatchSize) {
                break;
            }
        }
        if (batchCount > 0 || batchFailed) {
            log.info("[PaymentCallback] fallback DB dispatch finished, batches={}, claimed={}, inboxWritten={}, refunds={}, failed={}, batchSize={}, maxBatches={}, batchFailed={}",
                    batchCount, totalClaimed, totalWritten, totalRefunds, totalFailed, effectiveBatchSize, maxBatches, batchFailed);
        }
    }

    private int effectiveBatchSize() {
        return Math.max(1, Math.min(properties.getBatchSize(), 500));
    }

    private int maxBatchesPerRun() {
        return Math.max(1, properties.getMaxBatchesPerRun());
    }
}
