package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.mapper.order.PaymentRefundMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentRefundDispatchService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundDispatchService.class);

    private final PaymentRefundMapper paymentRefundMapper;
    private final PaymentRefundProvider paymentRefundProvider;
    private final PaymentRefundDispatchProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PaymentRefundDispatchService(PaymentRefundMapper paymentRefundMapper,
                                        PaymentRefundProvider paymentRefundProvider,
                                        PaymentRefundDispatchProperties properties,
                                        ObjectMapper objectMapper,
                                        TransactionTemplate transactionTemplate) {
        this.paymentRefundMapper = paymentRefundMapper;
        this.paymentRefundProvider = paymentRefundProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public DispatchSummary dispatchAvailable(Integer rawLimit) {
        if (!properties.isEnabled()) {
            return new DispatchSummary(0, 0);
        }
        int limit = normalizeLimit(rawLimit);
        List<Map<String, Object>> claimed = transactionTemplate.execute(status ->
                paymentRefundMapper.claimDispatchBatch(limit, Math.max(1, properties.getMaxRetry()))
        );
        if (claimed == null || claimed.isEmpty()) {
            return new DispatchSummary(0, 0);
        }
        List<PaymentRefundDispatchItem> items = claimed.stream()
                .map(this::toDispatchItem)
                .toList();
        List<PaymentRefundDispatchResult> providerResults = refundWithProvider(items);
        List<PaymentRefundDispatchResult> completeResults = completeResults(items, providerResults);
        int written = paymentRefundMapper.batchWriteDispatchResults(toResultsJson(completeResults));
        log.info("[Refund] dispatch finished, claimed={}, written={}", claimed.size(), written);
        return new DispatchSummary(claimed.size(), written);
    }

    private List<PaymentRefundDispatchResult> refundWithProvider(List<PaymentRefundDispatchItem> items) {
        try {
            return paymentRefundProvider.refund(
                    items,
                    Math.max(1, properties.getMaxRetry()),
                    Math.max(1000L, properties.getRetryBackoffBaseMillis())
            );
        } catch (Exception e) {
            log.warn("[Refund] provider batch failed, size={}", items.size(), e);
            OffsetDateTime now = OffsetDateTime.now();
            return items.stream()
                    .map(item -> providerFailure(item, now))
                    .toList();
        }
    }

    private List<PaymentRefundDispatchResult> completeResults(List<PaymentRefundDispatchItem> items,
                                                              List<PaymentRefundDispatchResult> providerResults) {
        Map<String, PaymentRefundDispatchResult> resultByRefundNo = providerResults == null
                ? Map.of()
                : providerResults.stream()
                .filter(result -> result.refundNo() != null && !result.refundNo().isBlank())
                .collect(Collectors.toMap(
                        PaymentRefundDispatchResult::refundNo,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        OffsetDateTime now = OffsetDateTime.now();
        return items.stream()
                .map(item -> resultByRefundNo.getOrDefault(item.refundNo(), providerFailure(item, now)))
                .toList();
    }

    private PaymentRefundDispatchResult providerFailure(PaymentRefundDispatchItem item, OffsetDateTime now) {
        int nextRetryCount = item.retryCount() + 1;
        OffsetDateTime nextRetryAt = nextRetryCount >= Math.max(1, properties.getMaxRetry())
                ? null
                : now.plusNanos(Math.max(1000L, properties.getRetryBackoffBaseMillis()) * 1_000_000L);
        return new PaymentRefundDispatchResult(
                item.refundNo(),
                PaymentRefundStatus.REFUND_FAILED,
                null,
                "REFUND_PROVIDER_BATCH_FAILED",
                "Refund provider batch execution failed.",
                nextRetryCount,
                nextRetryAt,
                null
        );
    }

    private PaymentRefundDispatchItem toDispatchItem(Map<String, Object> row) {
        return new PaymentRefundDispatchItem(
                OrderRowMapper.text(row, "refundNo"),
                OrderRowMapper.text(row, "orderNo"),
                OrderRowMapper.text(row, "paymentProvider"),
                OrderRowMapper.text(row, "externalTradeNo"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "refundAmountYuan")),
                OrderRowMapper.intValue(row, "retryCount", 0)
        );
    }

    private String toResultsJson(List<PaymentRefundDispatchResult> results) {
        List<Map<String, Object>> rows = results.stream()
                .map(this::toResultRow)
                .toList();
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new OrderServiceException(
                    "ORDER_REFUND_DISPATCH_RESULT_INVALID",
                    "Refund dispatch result is invalid.",
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private Map<String, Object> toResultRow(PaymentRefundDispatchResult result) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("refund_no", result.refundNo());
        row.put("status", normalizeResultStatus(result.status()));
        row.put("external_refund_no", blankToNull(result.externalRefundNo()));
        row.put("last_error_code", blankToNull(result.lastErrorCode()));
        row.put("last_error_message", blankToNull(result.lastErrorMessage()));
        row.put("retry_count", Math.max(0, result.retryCount()));
        row.put("next_retry_at_epoch_ms", epochMs(result.nextRetryAt()));
        row.put("refunded_at_epoch_ms", epochMs(result.refundedAt()));
        return row;
    }

    private String normalizeResultStatus(String status) {
        if (PaymentRefundStatus.REFUNDED.equals(status)) {
            return PaymentRefundStatus.REFUNDED;
        }
        return PaymentRefundStatus.REFUND_FAILED;
    }

    private Long epochMs(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant().toEpochMilli();
    }

    private String blankToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int normalizeLimit(Integer rawLimit) {
        int value = rawLimit == null || rawLimit <= 0 ? properties.getBatchSize() : rawLimit;
        return Math.max(1, Math.min(value, 500));
    }

    public record DispatchSummary(int claimedCount,
                                  int writtenCount) {
    }
}
