package com.example.ShoppingSystem.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentRefundDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundDispatchScheduler.class);

    private final PaymentRefundDispatchService paymentRefundDispatchService;
    private final PaymentRefundDispatchProperties properties;

    public PaymentRefundDispatchScheduler(PaymentRefundDispatchService paymentRefundDispatchService,
                                          PaymentRefundDispatchProperties properties) {
        this.paymentRefundDispatchService = paymentRefundDispatchService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.refund.dispatch.fallback-db-scan-interval-millis:300000}")
    public void dispatchPendingRefunds() {
        if (!properties.isEnabled() || !properties.isDbScanEnabled()) {
            return;
        }
        int effectiveBatchSize = effectiveBatchSize();
        int maxBatches = maxBatchesPerRun();
        int batchCount = 0;
        int totalClaimed = 0;
        int totalWritten = 0;
        boolean failed = false;
        while (batchCount < maxBatches) {
            PaymentRefundDispatchService.DispatchSummary summary;
            try {
                summary = paymentRefundDispatchService.dispatchAvailable(properties.getBatchSize());
            } catch (Exception e) {
                failed = true;
                log.warn("[Refund] fallback DB dispatch batch failed, batch={}, batchSize={}, totalClaimed={}, error={}",
                        batchCount + 1, effectiveBatchSize, totalClaimed, e.getMessage());
                break;
            }
            if (summary.claimedCount() <= 0) {
                break;
            }
            batchCount++;
            totalClaimed += summary.claimedCount();
            totalWritten += summary.writtenCount();
            if (summary.claimedCount() < effectiveBatchSize) {
                break;
            }
        }
        if (batchCount > 0 || failed) {
            log.info("[Refund] fallback DB dispatch finished, batches={}, claimed={}, written={}, batchSize={}, maxBatches={}, failed={}",
                    batchCount, totalClaimed, totalWritten, effectiveBatchSize, maxBatches, failed);
        }
    }

    private int effectiveBatchSize() {
        return Math.max(1, Math.min(properties.getBatchSize(), 500));
    }

    private int maxBatchesPerRun() {
        return Math.max(1, properties.getMaxBatchesPerRun());
    }
}
