package com.example.ShoppingSystem.order.service;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SimulatedPaymentRefundProvider implements PaymentRefundProvider {

    @Override
    public List<PaymentRefundDispatchResult> refund(List<PaymentRefundDispatchItem> items,
                                                    int maxRetry,
                                                    long retryBackoffBaseMillis) {
        OffsetDateTime now = OffsetDateTime.now();
        return items.stream()
                .map(item -> result(item, maxRetry, retryBackoffBaseMillis, now))
                .toList();
    }

    private PaymentRefundDispatchResult result(PaymentRefundDispatchItem item,
                                               int maxRetry,
                                               long retryBackoffBaseMillis,
                                               OffsetDateTime now) {
        if ("SIMULATED".equalsIgnoreCase(item.paymentProvider())) {
            return new PaymentRefundDispatchResult(
                    item.refundNo(),
                    PaymentRefundStatus.REFUNDED,
                    "SIM-REFUND-" + item.refundNo(),
                    null,
                    null,
                    item.retryCount(),
                    null,
                    now
            );
        }
        int nextRetryCount = item.retryCount() + 1;
        OffsetDateTime nextRetryAt = nextRetryCount >= Math.max(1, maxRetry)
                ? null
                : now.plusNanos(backoffMillis(nextRetryCount, retryBackoffBaseMillis) * 1_000_000L);
        return new PaymentRefundDispatchResult(
                item.refundNo(),
                PaymentRefundStatus.REFUND_FAILED,
                null,
                "REFUND_PROVIDER_NOT_CONFIGURED",
                "Payment refund provider is not configured: " + item.paymentProvider(),
                nextRetryCount,
                nextRetryAt,
                null
        );
    }

    private long backoffMillis(int retryCount, long retryBackoffBaseMillis) {
        long base = Math.max(1000L, retryBackoffBaseMillis);
        long multiplier = 1L << Math.min(Math.max(retryCount - 1, 0), 5);
        return Math.min(base * multiplier, 300_000L);
    }
}
