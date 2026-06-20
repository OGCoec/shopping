package com.example.ShoppingSystem.order.service.impl.PaymentRefundDispatchService;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.mapper.order.PaymentRefundMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.ShoppingSystem.order.service.PaymentRefundDispatchService;
import com.example.ShoppingSystem.order.service.OrderAmountCalculator;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.PaymentRefundDispatchItem;
import com.example.ShoppingSystem.order.service.PaymentRefundDispatchProperties;
import com.example.ShoppingSystem.order.service.PaymentRefundDispatchResult;
import com.example.ShoppingSystem.order.service.PaymentRefundProvider;
import com.example.ShoppingSystem.order.service.PaymentRefundStatus;
import com.example.ShoppingSystem.order.service.PaymentRefundStreamProperties;
import com.example.ShoppingSystem.order.service.PaymentRefundStreamRecord;
@Service
public class PaymentRefundDispatchServiceImpl implements PaymentRefundDispatchService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundDispatchService.class);

    private final PaymentRefundMapper paymentRefundMapper;
    private final PaymentRefundProvider paymentRefundProvider;
    private final PaymentRefundDispatchProperties properties;
    private final PaymentRefundStreamProperties streamProperties;
    private final ObjectMapper objectMapper;
    private final RoutedTransactionExecutor routedTransactionExecutor;

    public PaymentRefundDispatchServiceImpl(PaymentRefundMapper paymentRefundMapper,
                                        PaymentRefundProvider paymentRefundProvider,
                                        PaymentRefundDispatchProperties properties,
                                        PaymentRefundStreamProperties streamProperties,
                                        ObjectMapper objectMapper,
                                        RoutedTransactionExecutor routedTransactionExecutor) {
        this.paymentRefundMapper = paymentRefundMapper;
        this.paymentRefundProvider = paymentRefundProvider;
        this.properties = properties;
        this.streamProperties = streamProperties;
        this.objectMapper = objectMapper;
        this.routedTransactionExecutor = routedTransactionExecutor;
    }

    public DispatchSummary dispatchAvailable(Integer rawLimit) {
        if (!properties.isEnabled()) {
            return new DispatchSummary(0, 0);
        }
        int limit = normalizeLimit(rawLimit);
        List<Map<String, Object>> claimed = routedTransactionExecutor.execute(DataSourceRoute.TRADE, () ->
                paymentRefundMapper.claimDispatchBatch(limit, Math.max(1, properties.getMaxRetry()))
        );
        if (claimed == null || claimed.isEmpty()) {
            return new DispatchSummary(0, 0);
        }
        DispatchBatchResult result = dispatchClaimed(claimed);
        log.info("[Refund] dispatch finished, claimed={}, written={}", claimed.size(), result.writtenCount());
        return new DispatchSummary(claimed.size(), result.writtenCount());
    }

    public StreamDispatchSummary dispatchStreamRecords(List<PaymentRefundStreamRecord> records) {
        if (!properties.isEnabled() || records == null || records.isEmpty()) {
            return StreamDispatchSummary.empty();
        }
        List<Map<String, Object>> streamRows = routedTransactionExecutor.execute(DataSourceRoute.TRADE, () ->
                paymentRefundMapper.claimDispatchBatchByRefundNos(
                        toStreamRefundRowsJson(records),
                        Math.max(1, properties.getMaxRetry()),
                        Math.max(1L, streamProperties.getProcessingTimeoutMs())
                )
        );
        if (streamRows == null || streamRows.isEmpty()) {
            return StreamDispatchSummary.empty();
        }
        List<String> ackStreamMessageIds = new ArrayList<>(streamRows.stream()
                .filter(row -> booleanValue(row.get("streamAckOnly")))
                .map(row -> OrderRowMapper.text(row, "streamMessageId"))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList());
        List<Map<String, Object>> claimedRows = distinctRowsByRefundNo(streamRows.stream()
                .filter(row -> booleanValue(row.get("streamClaimed")))
                .toList());
        if (claimedRows.isEmpty()) {
            return new StreamDispatchSummary(0, 0, ackStreamMessageIds);
        }
        DispatchBatchResult result = dispatchClaimed(claimedRows);
        Map<String, List<String>> streamIdsByRefundNo = streamRows.stream()
                .filter(row -> booleanValue(row.get("streamClaimed")))
                .collect(Collectors.groupingBy(
                        row -> OrderRowMapper.text(row, "refundNo"),
                        LinkedHashMap::new,
                        Collectors.mapping(row -> OrderRowMapper.text(row, "streamMessageId"), Collectors.toList())
                ));
        terminalAckRefundNos(result.results()).stream()
                .flatMap(refundNo -> streamIdsByRefundNo.getOrDefault(refundNo, List.of()).stream())
                .filter(value -> !value.isBlank())
                .distinct()
                .forEach(ackStreamMessageIds::add);
        return new StreamDispatchSummary(claimedRows.size(), result.writtenCount(), ackStreamMessageIds.stream().distinct().toList());
    }

    private DispatchBatchResult dispatchClaimed(List<Map<String, Object>> claimed) {
        List<PaymentRefundDispatchItem> items = claimed.stream()
                .map(this::toDispatchItem)
                .toList();
        List<PaymentRefundDispatchResult> providerResults = refundWithProvider(items);
        List<PaymentRefundDispatchResult> completeResults = completeResults(items, providerResults);
        int written = routedTransactionExecutor.execute(DataSourceRoute.TRADE, () ->
                paymentRefundMapper.batchWriteDispatchResults(toResultsJson(completeResults))
        );
        return new DispatchBatchResult(written, completeResults);
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

    private String toStreamRefundRowsJson(List<PaymentRefundStreamRecord> records) {
        List<Map<String, Object>> rows = records.stream()
                .map(record -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("stream_message_id", record.streamMessageId());
                    row.put("refund_no", text(record.body(), "refundNo"));
                    return row;
                })
                .toList();
        return toJson(rows);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OrderServiceException(
                    "ORDER_REFUND_STREAM_PAYLOAD_INVALID",
                    "Refund stream payload is invalid.",
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private List<Map<String, Object>> distinctRowsByRefundNo(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(rows.stream()
                .filter(row -> !OrderRowMapper.text(row, "refundNo").isBlank())
                .collect(Collectors.toMap(
                        row -> OrderRowMapper.text(row, "refundNo"),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values());
    }

    private List<String> terminalAckRefundNos(List<PaymentRefundDispatchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        int maxRetry = Math.max(1, properties.getMaxRetry());
        return results.stream()
                .filter(result -> PaymentRefundStatus.REFUNDED.equals(result.status())
                        || (PaymentRefundStatus.REFUND_FAILED.equals(result.status()) && result.retryCount() >= maxRetry))
                .map(PaymentRefundDispatchResult::refundNo)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private String text(Map<String, String> body, String key) {
        if (body == null || key == null) {
            return "";
        }
        String value = body.get(key);
        return value == null ? "" : value.trim();
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private record DispatchBatchResult(int writtenCount,
                                       List<PaymentRefundDispatchResult> results) {
    }
}
