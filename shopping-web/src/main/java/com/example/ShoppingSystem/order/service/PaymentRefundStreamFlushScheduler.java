package com.example.ShoppingSystem.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentRefundStreamFlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundStreamFlushScheduler.class);

    private final PaymentRefundStreamService paymentRefundStreamService;
    private final PaymentRefundDispatchService paymentRefundDispatchService;
    private final PaymentRefundStreamProperties properties;

    public PaymentRefundStreamFlushScheduler(PaymentRefundStreamService paymentRefundStreamService,
                                             PaymentRefundDispatchService paymentRefundDispatchService,
                                             PaymentRefundStreamProperties properties) {
        this.paymentRefundStreamService = paymentRefundStreamService;
        this.paymentRefundDispatchService = paymentRefundDispatchService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.refund.stream.flush-interval-millis:5000}")
    public void flushRefundStream() {
        if (!properties.isEnabled()) {
            return;
        }
        int effectiveBatchSize = effectiveBatchSize();
        int maxBatches = maxBatchesPerRun();
        int batchCount = 0;
        int totalRead = 0;
        int totalClaimed = 0;
        int totalWritten = 0;
        long totalAcknowledged = 0L;
        boolean failed = false;
        while (batchCount < maxBatches) {
            List<PaymentRefundStreamRecord> records;
            try {
                records = paymentRefundStreamService.readBatch();
            } catch (Exception e) {
                failed = true;
                log.warn("[RefundStream] read batch failed, batch={}, batchSize={}, error={}",
                        batchCount + 1, effectiveBatchSize, e.getMessage());
                break;
            }
            if (records.isEmpty()) {
                break;
            }
            try {
                PaymentRefundDispatchService.StreamDispatchSummary summary = paymentRefundDispatchService.dispatchStreamRecords(records);
                long acknowledged = paymentRefundStreamService.ackAndDelete(summary.ackStreamMessageIds());
                batchCount++;
                totalRead += records.size();
                totalClaimed += summary.claimedCount();
                totalWritten += summary.writtenCount();
                totalAcknowledged += acknowledged;
                if (records.size() < effectiveBatchSize) {
                    break;
                }
            } catch (Exception e) {
                failed = true;
                log.warn("[RefundStream] flush batch failed, batch={}, read={}, error={}",
                        batchCount + 1, records.size(), e.getMessage());
                break;
            }
        }
        if (batchCount > 0 || failed) {
            log.info("[RefundStream] flush finished, batches={}, read={}, claimed={}, written={}, acked={}, failed={}, batchSize={}, maxBatches={}",
                    batchCount, totalRead, totalClaimed, totalWritten, totalAcknowledged, failed, effectiveBatchSize, maxBatches);
        }
    }

    private int effectiveBatchSize() {
        return Math.max(1, Math.min(properties.getBatchSize(), 500));
    }

    private int maxBatchesPerRun() {
        return Math.max(1, properties.getMaxBatchesPerRun());
    }
}
