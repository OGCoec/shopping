package com.example.ShoppingSystem.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentCallbackStreamFlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackStreamFlushScheduler.class);

    private final PaymentCallbackStreamService paymentCallbackStreamService;
    private final PaymentCallbackDispatchService paymentCallbackDispatchService;
    private final PaymentCallbackStreamProperties properties;

    public PaymentCallbackStreamFlushScheduler(PaymentCallbackStreamService paymentCallbackStreamService,
                                               PaymentCallbackDispatchService paymentCallbackDispatchService,
                                               PaymentCallbackStreamProperties properties) {
        this.paymentCallbackStreamService = paymentCallbackStreamService;
        this.paymentCallbackDispatchService = paymentCallbackDispatchService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.payment-callback.stream.flush-interval-millis:5000}")
    public void flushCallbackStream() {
        if (!properties.isEnabled()) {
            return;
        }
        int effectiveBatchSize = effectiveBatchSize();
        int maxBatches = maxBatchesPerRun();
        int batchCount = 0;
        int totalRead = 0;
        int totalClaimed = 0;
        int totalRefunds = 0;
        int totalFailed = 0;
        long totalAcknowledged = 0L;
        boolean batchFailed = false;
        while (batchCount < maxBatches) {
            List<PaymentCallbackStreamRecord> records;
            try {
                records = paymentCallbackStreamService.readBatch();
            } catch (Exception e) {
                batchFailed = true;
                log.warn("[PaymentCallbackStream] read batch failed, batch={}, batchSize={}, error={}",
                        batchCount + 1, effectiveBatchSize, e.getMessage());
                break;
            }
            if (records.isEmpty()) {
                break;
            }
            try {
                PaymentCallbackDispatchService.StreamDispatchSummary summary = paymentCallbackDispatchService.dispatchStreamRecords(records);
                long acknowledged = paymentCallbackStreamService.ackAndDelete(summary.ackStreamMessageIds());
                batchCount++;
                totalRead += records.size();
                totalClaimed += summary.claimedCount();
                totalRefunds += summary.refundCount();
                totalFailed += summary.failedCount();
                totalAcknowledged += acknowledged;
                if (records.size() < effectiveBatchSize) {
                    break;
                }
            } catch (Exception e) {
                batchFailed = true;
                log.warn("[PaymentCallbackStream] flush batch failed, batch={}, read={}, error={}",
                        batchCount + 1, records.size(), e.getMessage());
                break;
            }
        }
        if (batchCount > 0 || batchFailed) {
            log.info("[PaymentCallbackStream] flush finished, batches={}, read={}, claimed={}, acked={}, refunds={}, eventFailed={}, batchSize={}, maxBatches={}, batchFailed={}",
                    batchCount, totalRead, totalClaimed, totalAcknowledged, totalRefunds, totalFailed, effectiveBatchSize, maxBatches, batchFailed);
        }
    }

    private int effectiveBatchSize() {
        return Math.max(1, Math.min(properties.getBatchSize(), 500));
    }

    private int maxBatchesPerRun() {
        return Math.max(1, properties.getMaxBatchesPerRun());
    }
}
