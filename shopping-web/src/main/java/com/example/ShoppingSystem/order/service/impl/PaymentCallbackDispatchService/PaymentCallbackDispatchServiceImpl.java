package com.example.ShoppingSystem.order.service.impl.PaymentCallbackDispatchService;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.mapper.coupon.CouponUsageRecordMapper;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.mapper.order.PaymentCallbackInboxMapper;
import com.example.ShoppingSystem.mapper.order.PaymentRefundMapper;
import com.example.ShoppingSystem.order.rabbit.PaymentRefundMessagePublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.ShoppingSystem.order.service.PaymentCallbackDispatchService;
import com.example.ShoppingSystem.order.service.OrderAmountCalculator;
import com.example.ShoppingSystem.order.service.OrderCardSecretDeliveryService;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderStatus;
import com.example.ShoppingSystem.order.service.PaymentCallbackDispatchProperties;
import com.example.ShoppingSystem.order.service.PaymentCallbackEvent;
import com.example.ShoppingSystem.order.service.PaymentCallbackInboxStatus;
import com.example.ShoppingSystem.order.service.PaymentCallbackOutcome;
import com.example.ShoppingSystem.order.service.PaymentCallbackStreamProperties;
import com.example.ShoppingSystem.order.service.PaymentCallbackStreamRecord;
import com.example.ShoppingSystem.order.service.PaymentRefundReasonCode;
import com.example.ShoppingSystem.order.service.PaymentRefundSource;
import com.example.ShoppingSystem.order.service.PaymentRefundStreamProperties;
import com.example.ShoppingSystem.order.service.PaymentRefundStreamService;
@Service
public class PaymentCallbackDispatchServiceImpl implements PaymentCallbackDispatchService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackDispatchService.class);

    private static final String DEFAULT_CURRENCY = "CNY";
    private static final String DEFAULT_PROVIDER = "SIMULATED";

    private final PaymentCallbackInboxMapper paymentCallbackInboxMapper;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderMapper orderMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponUsageRecordMapper couponUsageRecordMapper;
    private final PaymentRefundMapper paymentRefundMapper;
    private final PaymentRefundMessagePublisher paymentRefundMessagePublisher;
    private final PaymentRefundStreamService paymentRefundStreamService;
    private final PaymentRefundStreamProperties paymentRefundStreamProperties;
    private final OrderCardSecretDeliveryService orderCardSecretDeliveryService;
    private final PaymentCallbackDispatchProperties properties;
    private final PaymentCallbackStreamProperties paymentCallbackStreamProperties;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final ObjectMapper objectMapper;
    private final RoutedTransactionExecutor routedTransactionExecutor;

    public PaymentCallbackDispatchServiceImpl(PaymentCallbackInboxMapper paymentCallbackInboxMapper,
                                          OrderRedisSnapshotService orderRedisSnapshotService,
                                          OrderMapper orderMapper,
                                          UserCouponMapper userCouponMapper,
                                          CouponUsageRecordMapper couponUsageRecordMapper,
                                          PaymentRefundMapper paymentRefundMapper,
                                          PaymentRefundMessagePublisher paymentRefundMessagePublisher,
                                          PaymentRefundStreamService paymentRefundStreamService,
                                          PaymentRefundStreamProperties paymentRefundStreamProperties,
                                          OrderCardSecretDeliveryService orderCardSecretDeliveryService,
                                          PaymentCallbackDispatchProperties properties,
                                          PaymentCallbackStreamProperties paymentCallbackStreamProperties,
                                          HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                          ObjectMapper objectMapper,
                                          RoutedTransactionExecutor routedTransactionExecutor) {
        this.paymentCallbackInboxMapper = paymentCallbackInboxMapper;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.orderMapper = orderMapper;
        this.userCouponMapper = userCouponMapper;
        this.couponUsageRecordMapper = couponUsageRecordMapper;
        this.paymentRefundMapper = paymentRefundMapper;
        this.paymentRefundMessagePublisher = paymentRefundMessagePublisher;
        this.paymentRefundStreamService = paymentRefundStreamService;
        this.paymentRefundStreamProperties = paymentRefundStreamProperties;
        this.orderCardSecretDeliveryService = orderCardSecretDeliveryService;
        this.properties = properties;
        this.paymentCallbackStreamProperties = paymentCallbackStreamProperties;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.objectMapper = objectMapper;
        this.routedTransactionExecutor = routedTransactionExecutor;
    }

    public DispatchSummary dispatchAvailable(Integer rawLimit) {
        if (!properties.isEnabled()) {
            return new DispatchSummary(0, 0, 0, 0);
        }
        int limit = normalizeLimit(rawLimit);
        List<Map<String, Object>> claimed = routedTransactionExecutor.execute(DataSourceRoute.TRADE, () ->
                paymentCallbackInboxMapper.claimDispatchBatch(limit, Math.max(1, properties.getMaxRetry()))
        );
        if (claimed == null || claimed.isEmpty()) {
            return new DispatchSummary(0, 0, 0, 0);
        }
        try {
            DispatchBatchResult result = processClaimed(claimed);
            orderCardSecretDeliveryService.deliverPaidOrdersFromRows(result.paidRows());
            enqueueRefundDispatchMessages(result.refundNos(), false);
            log.info("[PaymentCallback] dispatch finished, claimed={}, inboxWritten={}, refunds={}",
                    claimed.size(), result.inboxWrittenCount(), result.refundNos().size());
            return new DispatchSummary(claimed.size(), result.inboxWrittenCount(), result.refundNos().size(), 0);
        } catch (Exception e) {
            int failed = writeFailureResults(claimed, "PAYMENT_CALLBACK_BATCH_FAILED", "Payment callback batch failed.");
            log.warn("[PaymentCallback] dispatch batch failed, claimed={}, failedWritten={}", claimed.size(), failed, e);
            return new DispatchSummary(claimed.size(), 0, 0, failed);
        }
    }

    public StreamDispatchSummary dispatchStreamRecords(List<PaymentCallbackStreamRecord> records) {
        if (!properties.isEnabled() || records == null || records.isEmpty()) {
            return StreamDispatchSummary.empty();
        }
        List<Map<String, Object>> streamRows = routedTransactionExecutor.execute(DataSourceRoute.TRADE, () ->
                paymentCallbackInboxMapper.batchUpsertAndClaimStreamCallbacks(
                        toStreamCallbackRowsJson(records),
                        Math.max(1, properties.getMaxRetry()),
                        Math.max(1L, paymentCallbackStreamProperties.getProcessingTimeoutMs())
                )
        );
        if (streamRows == null || streamRows.isEmpty()) {
            return StreamDispatchSummary.empty();
        }
        List<Map<String, Object>> ackOnlyRows = streamRows.stream()
                .filter(row -> booleanValue(row.get("streamAckOnly")))
                .toList();
        List<Map<String, Object>> claimedRows = distinctRowsByCallbackNo(streamRows.stream()
                .filter(row -> booleanValue(row.get("streamClaimed")))
                .toList());
        List<String> ackStreamMessageIds = streamRows.stream()
                .filter(row -> booleanValue(row.get("streamAckOnly")) || booleanValue(row.get("streamClaimed")))
                .map(row -> OrderRowMapper.text(row, "streamMessageId"))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        List<String> refundNos = new ArrayList<>(refundNosFromRows(ackOnlyRows));
        if (claimedRows.isEmpty()) {
            enqueueRefundDispatchMessages(refundNos, true);
            return new StreamDispatchSummary(0, 0, refundNos.size(), 0, ackStreamMessageIds);
        }
        try {
            DispatchBatchResult result = processClaimed(claimedRows);
            orderCardSecretDeliveryService.deliverPaidOrdersFromRows(result.paidRows());
            refundNos.addAll(result.refundNos());
            refundNos = refundNos.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .toList();
            enqueueRefundDispatchMessages(refundNos, true);
            return new StreamDispatchSummary(claimedRows.size(), result.inboxWrittenCount(), refundNos.size(), 0, ackStreamMessageIds);
        } catch (Exception e) {
            int failed = writeFailureResults(claimedRows, "PAYMENT_CALLBACK_STREAM_BATCH_FAILED", "Payment callback stream batch failed.");
            log.warn("[PaymentCallbackStream] dispatch batch failed, claimed={}, failedWritten={}", claimedRows.size(), failed, e);
            return new StreamDispatchSummary(claimedRows.size(), 0, 0, failed, List.of());
        }
    }

    private DispatchBatchResult processClaimed(List<Map<String, Object>> claimed) {
        List<PaymentCallbackEvent> events = claimed.stream()
                .map(this::toEvent)
                .toList();
        List<Map<String, Object>> redisResults = orderRedisSnapshotService.markPaidBatch(events);
        Map<String, PaymentCallbackEvent> eventByCallbackNo = events.stream()
                .collect(Collectors.toMap(PaymentCallbackEvent::callbackNo, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<PaymentCallbackEvent> missingEvents = redisResults.stream()
                .filter(row -> "MISSING".equals(OrderRowMapper.text(row, "outcome")))
                .map(row -> eventByCallbackNo.get(OrderRowMapper.text(row, "callbackNo")))
                .filter(event -> event != null)
                .toList();
        return routedTransactionExecutor.execute(DataSourceRoute.TRADE, () -> processDatabaseWork(events, redisResults, missingEvents));
    }

    private DispatchBatchResult processDatabaseWork(List<PaymentCallbackEvent> events,
                                                    List<Map<String, Object>> redisResults,
                                                    List<PaymentCallbackEvent> missingEvents) {
        List<Map<String, Object>> dbResults = missingEvents.isEmpty()
                ? List.of()
                : orderMapper.batchMarkPaidAndClassify(toCallbackRowsJson(missingEvents));
        List<Map<String, Object>> classified = new ArrayList<>(redisResults.size() + dbResults.size());
        redisResults.stream()
                .filter(row -> !"MISSING".equals(OrderRowMapper.text(row, "outcome")))
                .forEach(classified::add);
        classified.addAll(dbResults);

        useCouponsForPaidOrders(classified);
        RefundWriteResult refundWriteResult = writeRefunds(classified);

        Map<String, Map<String, Object>> classifiedByCallbackNo = classified.stream()
                .filter(row -> !OrderRowMapper.text(row, "callbackNo").isBlank())
                .collect(Collectors.toMap(
                        row -> OrderRowMapper.text(row, "callbackNo"),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<Map<String, Object>> inboxResults = events.stream()
                .map(event -> inboxResult(event, classifiedByCallbackNo.get(event.callbackNo()), refundWriteResult))
                .toList();
        int written = paymentCallbackInboxMapper.batchWriteResults(toJson(inboxResults));
        List<Map<String, Object>> paidRows = classified.stream()
                .filter(row -> {
                    String outcome = OrderRowMapper.text(row, "outcome");
                    return PaymentCallbackOutcome.PAID.equals(outcome) || PaymentCallbackOutcome.PAID_IDEMPOTENT.equals(outcome);
                })
                .toList();
        return new DispatchBatchResult(written, refundWriteResult.refundNos(), paidRows);
    }

    private void useCouponsForPaidOrders(List<Map<String, Object>> classified) {
        List<Map<String, Object>> paidOrders = classified.stream()
                .filter(row -> {
                    String outcome = OrderRowMapper.text(row, "outcome");
                    return PaymentCallbackOutcome.PAID.equals(outcome) || PaymentCallbackOutcome.PAID_IDEMPOTENT.equals(outcome);
                })
                .map(this::couponOrderRow)
                .filter(row -> row != null)
                .toList();
        if (paidOrders.isEmpty()) {
            return;
        }
        List<Map<String, Object>> usedCoupons = userCouponMapper.useLockedCouponsByOrderNos(toJson(paidOrders));
        if (usedCoupons == null || usedCoupons.isEmpty()) {
            return;
        }
        List<Map<String, Object>> usageRecords = usedCoupons.stream()
                .map(this::couponUsageRecordRow)
                .toList();
        couponUsageRecordMapper.batchInsertUsageRecordsIgnore(toJson(usageRecords));
    }

    private Map<String, Object> couponOrderRow(Map<String, Object> row) {
        String orderNo = OrderRowMapper.text(row, "orderNo");
        Long paidAtEpochMs = OrderRowMapper.longValue(row, "paidAtEpochMs");
        if (orderNo.isBlank() || paidAtEpochMs == null) {
            return null;
        }
        Map<String, Object> couponRow = new LinkedHashMap<>();
        couponRow.put("order_no", orderNo);
        couponRow.put("user_id", OrderRowMapper.longValue(row, "userId"));
        couponRow.put("paid_at_epoch_ms", paidAtEpochMs);
        couponRow.put("order_amount_yuan", OrderAmountCalculator.money(OrderRowMapper.decimal(row, "totalAmountYuan")));
        couponRow.put("discount_amount_yuan", OrderAmountCalculator.money(OrderRowMapper.decimal(row, "discountAmountYuan")));
        return couponRow;
    }

    private Map<String, Object> couponUsageRecordRow(Map<String, Object> row) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id_hex", HybridIdCodec.toHex(hybridSemaphoreIdWorker.nextId()));
        record.put("user_coupon_id_hex", OrderRowMapper.text(row, "userCouponId"));
        record.put("coupon_template_id_hex", OrderRowMapper.text(row, "couponTemplateId"));
        record.put("user_id", OrderRowMapper.longValue(row, "userId"));
        record.put("order_no", OrderRowMapper.text(row, "orderNo"));
        record.put("action", "USE");
        record.put("order_amount_yuan", OrderAmountCalculator.money(OrderRowMapper.decimal(row, "orderAmountYuan")));
        record.put("discount_amount_yuan", OrderAmountCalculator.money(OrderRowMapper.decimal(row, "discountAmountYuan")));
        record.put("idempotency_key", "ORDER_COUPON_USE:" + OrderRowMapper.text(row, "orderNo"));
        return record;
    }

    private RefundWriteResult writeRefunds(List<Map<String, Object>> classified) {
        List<Map<String, Object>> refundCandidates = classified.stream()
                .filter(row -> PaymentCallbackOutcome.REFUND_PENDING.equals(OrderRowMapper.text(row, "outcome")))
                .toList();
        if (refundCandidates.isEmpty()) {
            return RefundWriteResult.empty();
        }
        String detectionBatchNo = "PCB-" + nextBase62();
        List<Map<String, Object>> refundRows = new ArrayList<>(refundCandidates.size());
        Map<String, Map<String, Object>> failures = new LinkedHashMap<>();
        for (Map<String, Object> candidate : refundCandidates) {
            BigDecimal paidAmount = refundAmount(candidate);
            String callbackNo = OrderRowMapper.text(candidate, "callbackNo");
            if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
                failures.put(callbackNo, failureResult(
                        callbackNo,
                        OrderRowMapper.text(candidate, "orderStatus"),
                        "ORDER_PAYMENT_AMOUNT_REQUIRED",
                        "paidAmountYuan is required when paid order cannot be found."
                ));
                continue;
            }
            refundRows.add(refundRow(candidate, paidAmount, detectionBatchNo));
        }
        if (refundRows.isEmpty()) {
            return new RefundWriteResult(Map.of(), failures, List.of());
        }
        List<Map<String, Object>> inserted = paymentRefundMapper.batchInsertRefundIgnore(toJson(refundRows));
        Map<String, Map<String, Object>> refundByCallbackNo = inserted == null
                ? Map.of()
                : inserted.stream()
                .filter(row -> !OrderRowMapper.text(row, "callbackNo").isBlank())
                .collect(Collectors.toMap(
                        row -> OrderRowMapper.text(row, "callbackNo"),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (Map<String, Object> refundRow : refundRows) {
            String callbackNo = OrderRowMapper.text(refundRow, "callback_no");
            if (!refundByCallbackNo.containsKey(callbackNo)) {
                failures.put(callbackNo, failureResult(
                        callbackNo,
                        OrderRowMapper.text(refundRow, "order_status_when_detected"),
                        "ORDER_REFUND_CREATE_FAILED",
                        "Refund record create failed."
                ));
            }
        }
        List<String> refundNos = refundByCallbackNo.values().stream()
                .map(row -> OrderRowMapper.text(row, "refundNo"))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        return new RefundWriteResult(refundByCallbackNo, failures, refundNos);
    }

    private Map<String, Object> refundRow(Map<String, Object> candidate,
                                          BigDecimal paidAmount,
                                          String detectionBatchNo) {
        String orderNo = OrderRowMapper.text(candidate, "orderNo");
        String externalTradeNo = OrderRowMapper.text(candidate, "externalTradeNo");
        String reasonCode = normalizeReasonCode(OrderRowMapper.text(candidate, "reasonCode"), OrderRowMapper.text(candidate, "orderStatus"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("callback_no", OrderRowMapper.text(candidate, "callbackNo"));
        row.put("refund_no", nextBase62());
        row.put("order_no", orderNo);
        row.put("user_id", OrderRowMapper.longValue(candidate, "userId"));
        row.put("payment_provider", normalizeProvider(OrderRowMapper.text(candidate, "paymentProvider")));
        row.put("external_trade_no", blankToNull(externalTradeNo));
        row.put("payment_callback_id", OrderRowMapper.text(candidate, "callbackNo"));
        row.put("paid_amount_yuan", paidAmount);
        row.put("refund_amount_yuan", paidAmount);
        row.put("currency", DEFAULT_CURRENCY);
        row.put("source", PaymentRefundSource.PAYMENT_CALLBACK);
        row.put("reason_code", reasonCode);
        row.put("reason_detail", abnormalReasonDetail(reasonCode, OrderRowMapper.text(candidate, "orderStatus")));
        row.put("order_status_when_detected", normalizeOrderStatus(OrderRowMapper.text(candidate, "orderStatus")));
        row.put("detected_at_epoch_ms", OffsetDateTime.now().toInstant().toEpochMilli());
        row.put("detection_batch_no", detectionBatchNo);
        row.put("user_message", "Payment succeeded but order is abnormal; refund request has been created.");
        row.put("snapshot_json", toJson(snapshotPayload(candidate, paidAmount)));
        row.put("extra_json", toJson(Map.of("callbackNo", OrderRowMapper.text(candidate, "callbackNo"))));
        row.put("idempotency_key", stableIdempotencyKey("refund:callback", orderNo, externalTradeNo.isBlank() ? "NO_EXTERNAL_TRADE" : externalTradeNo));
        return row;
    }

    private Map<String, Object> inboxResult(PaymentCallbackEvent event,
                                            Map<String, Object> classified,
                                            RefundWriteResult refundWriteResult) {
        Map<String, Object> failure = refundWriteResult.failures().get(event.callbackNo());
        if (failure != null) {
            return failure;
        }
        if (classified == null || classified.isEmpty()) {
            return failureResult(event.callbackNo(), "UNKNOWN", "PAYMENT_CALLBACK_RESULT_MISSING", "Payment callback result is missing.");
        }
        String outcome = OrderRowMapper.text(classified, "outcome");
        if (PaymentCallbackOutcome.PAID.equals(outcome) || PaymentCallbackOutcome.PAID_IDEMPOTENT.equals(outcome)) {
            return processedResult(
                    event.callbackNo(),
                    outcome,
                    normalizeOrderStatus(OrderRowMapper.text(classified, "orderStatus")),
                    null
            );
        }
        if (PaymentCallbackOutcome.REFUND_PENDING.equals(outcome)) {
            Map<String, Object> refund = refundWriteResult.refundsByCallbackNo().get(event.callbackNo());
            if (refund == null || refund.isEmpty()) {
                return failureResult(event.callbackNo(), normalizeOrderStatus(OrderRowMapper.text(classified, "orderStatus")), "ORDER_REFUND_CREATE_FAILED", "Refund record create failed.");
            }
            return processedResult(
                    event.callbackNo(),
                    PaymentCallbackOutcome.REFUND_PENDING,
                    normalizeOrderStatus(OrderRowMapper.text(classified, "orderStatus")),
                    OrderRowMapper.text(refund, "refundNo")
            );
        }
        return failureResult(event.callbackNo(), normalizeOrderStatus(OrderRowMapper.text(classified, "orderStatus")), "PAYMENT_CALLBACK_OUTCOME_INVALID", "Payment callback outcome is invalid.");
    }

    private Map<String, Object> processedResult(String callbackNo,
                                                String outcome,
                                                String orderStatus,
                                                String refundNo) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("callback_no", callbackNo);
        row.put("status", PaymentCallbackInboxStatus.PROCESSED);
        row.put("result_outcome", outcome);
        row.put("result_order_status", normalizeOrderStatus(orderStatus));
        row.put("refund_no", blankToNull(refundNo));
        row.put("last_error_code", null);
        row.put("last_error_message", null);
        row.put("next_retry_at_epoch_ms", null);
        return row;
    }

    private Map<String, Object> failureResult(String callbackNo,
                                              String orderStatus,
                                              String errorCode,
                                              String errorMessage) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("callback_no", callbackNo);
        row.put("status", PaymentCallbackInboxStatus.FAILED);
        row.put("result_outcome", PaymentCallbackOutcome.FAILED);
        row.put("result_order_status", normalizeOrderStatus(orderStatus));
        row.put("refund_no", null);
        row.put("last_error_code", errorCode);
        row.put("last_error_message", errorMessage);
        row.put("next_retry_at_epoch_ms", OffsetDateTime.now()
                .plusNanos(Math.max(1000L, properties.getRetryBackoffBaseMillis()) * 1_000_000L)
                .toInstant()
                .toEpochMilli());
        return row;
    }

    private int writeFailureResults(List<Map<String, Object>> claimed, String errorCode, String errorMessage) {
        List<Map<String, Object>> results = claimed.stream()
                .map(row -> failureResult(
                        OrderRowMapper.text(row, "callbackNo"),
                        normalizeOrderStatus(OrderRowMapper.text(row, "resultOrderStatus")),
                        errorCode,
                        errorMessage
                ))
                .toList();
        return paymentCallbackInboxMapper.batchWriteResults(toJson(results));
    }

    private PaymentCallbackEvent toEvent(Map<String, Object> row) {
        return new PaymentCallbackEvent(
                OrderRowMapper.text(row, "callbackNo"),
                OrderRowMapper.text(row, "orderNo"),
                OrderRowMapper.text(row, "externalTradeNo"),
                normalizeProvider(OrderRowMapper.text(row, "paymentProvider")),
                OrderRowMapper.offsetDateTime(row, "paidAt"),
                OrderAmountCalculator.money(OrderRowMapper.nullableDecimal(row, "paidAmountYuan"))
        );
    }

    private String toCallbackRowsJson(List<PaymentCallbackEvent> events) {
        List<Map<String, Object>> rows = events.stream()
                .map(event -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("callback_no", event.callbackNo());
                    row.put("order_no", event.orderNo());
                    row.put("external_trade_no", blankToNull(event.externalTradeNo()));
                    row.put("payment_provider", normalizeProvider(event.paymentProvider()));
                    row.put("paid_at_epoch_ms", event.paidAt().toInstant().toEpochMilli());
                    row.put("paid_amount_yuan", event.paidAmountYuan());
                    return row;
                })
                .toList();
        return toJson(rows);
    }

    private BigDecimal refundAmount(Map<String, Object> candidate) {
        BigDecimal callbackAmount = positiveMoneyOrNull(OrderRowMapper.nullableDecimal(candidate, "paidAmountYuan"));
        if (callbackAmount != null) {
            return callbackAmount;
        }
        if (!"NOT_FOUND".equals(OrderRowMapper.text(candidate, "orderStatus"))) {
            return positiveMoneyOrNull(OrderRowMapper.nullableDecimal(candidate, "payAmountYuan"));
        }
        return null;
    }

    private BigDecimal positiveMoneyOrNull(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal amount = OrderAmountCalculator.money(value);
        return amount.compareTo(BigDecimal.ZERO) > 0 ? amount : null;
    }

    private Map<String, Object> snapshotPayload(Map<String, Object> candidate, BigDecimal paidAmount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callbackNo", OrderRowMapper.text(candidate, "callbackNo"));
        payload.put("orderNo", OrderRowMapper.text(candidate, "orderNo"));
        payload.put("externalTradeNo", OrderRowMapper.text(candidate, "externalTradeNo"));
        payload.put("paymentProvider", normalizeProvider(OrderRowMapper.text(candidate, "paymentProvider")));
        payload.put("paidAmountYuan", paidAmount);
        payload.put("orderStatus", normalizeOrderStatus(OrderRowMapper.text(candidate, "orderStatus")));
        payload.put("userId", OrderRowMapper.longValue(candidate, "userId"));
        payload.put("payAmountYuan", OrderRowMapper.nullableDecimal(candidate, "payAmountYuan"));
        return payload;
    }

    private List<Map<String, Object>> distinctRowsByCallbackNo(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(rows.stream()
                .filter(row -> !OrderRowMapper.text(row, "callbackNo").isBlank())
                .collect(Collectors.toMap(
                        row -> OrderRowMapper.text(row, "callbackNo"),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values());
    }

    private List<String> refundNosFromRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> PaymentCallbackOutcome.REFUND_PENDING.equals(OrderRowMapper.text(row, "resultOutcome")))
                .map(row -> OrderRowMapper.text(row, "refundNo"))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private void enqueueRefundDispatchMessages(List<String> refundNos, boolean strict) {
        if (refundNos == null || refundNos.isEmpty()) {
            return;
        }
        if (paymentRefundStreamProperties.isEnabled()) {
            try {
                paymentRefundStreamService.enqueueBatch(refundNos);
            } catch (Exception e) {
                log.warn("[PaymentCallback] refund stream enqueue failed, size={}", refundNos.size(), e);
                if (strict) {
                    throw e;
                }
            }
            return;
        }
        for (String refundNo : refundNos) {
            try {
                paymentRefundMessagePublisher.publish(refundNo);
            } catch (Exception e) {
                log.warn("[PaymentCallback] refund dispatch publish failed, refundNo={}", refundNo, e);
                if (strict) {
                    throw e;
                }
            }
        }
    }

    private String normalizeReasonCode(String reasonCode, String orderStatus) {
        String value = reasonCode == null ? "" : reasonCode.trim();
        if (!value.isBlank() && PaymentRefundReasonCode.ALL.contains(value)) {
            return value;
        }
        if (OrderStatus.CLOSED.equals(orderStatus)) {
            return PaymentRefundReasonCode.PAID_AFTER_ORDER_CLOSED;
        }
        if (OrderStatus.CANCELLED.equals(orderStatus)) {
            return PaymentRefundReasonCode.PAID_AFTER_ORDER_CANCELLED;
        }
        if ("NOT_FOUND".equals(orderStatus)) {
            return PaymentRefundReasonCode.ORDER_NOT_FOUND_AFTER_PAID;
        }
        return PaymentRefundReasonCode.OTHER;
    }

    private String abnormalReasonDetail(String reasonCode, String orderStatus) {
        return switch (reasonCode) {
            case PaymentRefundReasonCode.PAID_AFTER_ORDER_CLOSED -> "Payment callback arrived after order was closed.";
            case PaymentRefundReasonCode.PAID_AFTER_ORDER_CANCELLED -> "Payment callback arrived after order was cancelled.";
            case PaymentRefundReasonCode.ORDER_NOT_FOUND_AFTER_PAID -> "Payment callback arrived but order was not found.";
            default -> "Payment callback arrived but order status is not payable: " + normalizeOrderStatus(orderStatus);
        };
    }

    private String normalizeOrderStatus(String orderStatus) {
        String value = orderStatus == null ? "" : orderStatus.trim();
        if (value.isEmpty()) {
            return "UNKNOWN";
        }
        return switch (value) {
            case OrderStatus.PENDING_PAYMENT,
                    OrderStatus.CLOSING,
                    OrderStatus.PAID,
                    OrderStatus.CANCELLED,
                    OrderStatus.CLOSED,
                    "NOT_FOUND" -> value;
            default -> "UNKNOWN";
        };
    }

    private String normalizeProvider(String provider) {
        String value = provider == null ? "" : provider.trim();
        if (value.isEmpty()) {
            return DEFAULT_PROVIDER;
        }
        String upper = value.toUpperCase();
        return upper.length() > 32 ? upper.substring(0, 32) : upper;
    }

    private String blankToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String nextBase62() {
        return HybridIdCodec.toBase62(hybridSemaphoreIdWorker.nextId());
    }

    private String stableIdempotencyKey(String prefix, String... parts) {
        StringBuilder raw = new StringBuilder(prefix == null ? "" : prefix);
        for (String part : parts) {
            raw.append(':').append(part == null ? "" : part);
        }
        UUID uuid = UUID.nameUUIDFromBytes(raw.toString().getBytes(StandardCharsets.UTF_8));
        return prefix + ":" + uuid;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OrderServiceException("PAYMENT_CALLBACK_BATCH_JSON_INVALID", "Payment callback batch json is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private int normalizeLimit(Integer rawLimit) {
        int value = rawLimit == null || rawLimit <= 0 ? properties.getBatchSize() : rawLimit;
        return Math.max(1, Math.min(value, 500));
    }

    private String toStreamCallbackRowsJson(List<PaymentCallbackStreamRecord> records) {
        List<Map<String, Object>> rows = records.stream()
                .map(this::toStreamCallbackRow)
                .toList();
        return toJson(rows);
    }

    private Map<String, Object> toStreamCallbackRow(PaymentCallbackStreamRecord record) {
        Map<String, String> body = record.body();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stream_message_id", record.streamMessageId());
        row.put("callback_no", text(body, "callbackNo"));
        row.put("order_no", text(body, "orderNo"));
        row.put("external_trade_no", blankToNull(text(body, "externalTradeNo")));
        row.put("payment_provider", normalizeProvider(text(body, "paymentProvider")));
        row.put("paid_at_epoch_ms", longOrNull(text(body, "paidAtEpochMs")));
        row.put("paid_amount_yuan", decimalOrNull(text(body, "paidAmountYuan")));
        row.put("idempotency_key", text(body, "idempotencyKey"));
        row.put("raw_payload_json", text(body, "rawPayloadJson"));
        row.put("received_at_epoch_ms", longOrNull(text(body, "receivedAtEpochMs")));
        return row;
    }

    private String text(Map<String, String> body, String key) {
        if (body == null || key == null) {
            return "";
        }
        String value = body.get(key);
        return value == null ? "" : value.trim();
    }

    private Long longOrNull(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException e) {
            throw new OrderServiceException("PAYMENT_CALLBACK_STREAM_PAYLOAD_INVALID", "Payment callback stream payload is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private BigDecimal decimalOrNull(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return OrderAmountCalculator.money(new BigDecimal(normalized));
        } catch (NumberFormatException e) {
            throw new OrderServiceException("PAYMENT_CALLBACK_STREAM_PAYLOAD_INVALID", "Payment callback stream payload is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private record DispatchBatchResult(int inboxWrittenCount,
                                       List<String> refundNos,
                                       List<Map<String, Object>> paidRows) {
    }

    private record RefundWriteResult(Map<String, Map<String, Object>> refundsByCallbackNo,
                                     Map<String, Map<String, Object>> failures,
                                     List<String> refundNos) {
        static RefundWriteResult empty() {
            return new RefundWriteResult(Map.of(), Map.of(), List.of());
        }
    }
}
