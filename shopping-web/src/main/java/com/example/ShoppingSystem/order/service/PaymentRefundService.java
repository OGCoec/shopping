package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.config.datasource.OrderReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.mapper.order.PaymentRefundMapper;
import com.example.ShoppingSystem.order.dto.PaymentRefundApplyRequest;
import com.example.ShoppingSystem.order.dto.PaymentRefundPageResponse;
import com.example.ShoppingSystem.order.dto.PaymentRefundResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentRefundService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundService.class);

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_PROVIDER = "SIMULATED";
    private static final String DEFAULT_CURRENCY = "CNY";

    private final PaymentRefundMapper paymentRefundMapper;
    private final OrderMapper orderMapper;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final ObjectMapper objectMapper;
    private final PaymentRefundMessagePublisher paymentRefundMessagePublisher;
    private final OrderReadReplicaQueryExecutor orderReadReplicaQueryExecutor;

    public PaymentRefundService(PaymentRefundMapper paymentRefundMapper,
                                OrderMapper orderMapper,
                                OrderRedisSnapshotService orderRedisSnapshotService,
                                HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                ObjectMapper objectMapper,
                                PaymentRefundMessagePublisher paymentRefundMessagePublisher,
                                OrderReadReplicaQueryExecutor orderReadReplicaQueryExecutor) {
        this.paymentRefundMapper = paymentRefundMapper;
        this.orderMapper = orderMapper;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.objectMapper = objectMapper;
        this.paymentRefundMessagePublisher = paymentRefundMessagePublisher;
        this.orderReadReplicaQueryExecutor = orderReadReplicaQueryExecutor;
    }

    public PaymentRefundResponse createFromPaymentAbnormal(String orderNo,
                                                           String externalTradeNo,
                                                           OffsetDateTime paidAt,
                                                           BigDecimal paidAmountYuan,
                                                           String paymentProvider,
                                                           Map<String, Object> order,
                                                           String reasonCode) {
        String normalizedOrderNo = normalizeRequiredOrderNo(orderNo, false);
        String normalizedExternalTradeNo = normalizeExternalTradeNo(externalTradeNo);
        String normalizedProvider = normalizeProvider(paymentProvider);
        OffsetDateTime detectedAt = paidAt == null ? OffsetDateTime.now() : paidAt;
        String orderStatus = orderStatus(order);
        Long userId = order == null ? null : OrderRowMapper.longValue(order, "userId");
        BigDecimal paidAmount = resolvePaidAmount(paidAmountYuan, order, true);
        BigDecimal refundAmount = paidAmount;
        String actualReason = normalizeReasonCode(reasonCode, PaymentRefundReasonCode.OTHER);
        Map<String, Object> row = paymentRefundMapper.insertRefundIgnore(
                nextRefundNo(),
                normalizedOrderNo,
                userId,
                normalizedProvider,
                normalizedExternalTradeNo,
                null,
                paidAmount,
                refundAmount,
                DEFAULT_CURRENCY,
                PaymentRefundSource.PAYMENT_CALLBACK,
                actualReason,
                abnormalReasonDetail(actualReason, orderStatus),
                orderStatus,
                detectedAt,
                null,
                "支付成功但订单状态异常，系统已创建退款处理单。",
                toJson(snapshotPayload(order, paidAmountYuan, externalTradeNo, paymentProvider)),
                "{}",
                paymentCallbackIdempotencyKey(normalizedOrderNo, normalizedExternalTradeNo)
        );
        PaymentRefundResponse response = toResponse(row);
        publishDispatchQuietly(response.refundNo(), response.status());
        return response;
    }

    public PaymentRefundResponse applyForUser(Long userId,
                                              String orderNo,
                                              PaymentRefundApplyRequest request) {
        if (userId == null) {
            throw new OrderServiceException("ORDER_AUTH_REQUIRED", "Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        String normalizedOrderNo = normalizeRequiredOrderNo(orderNo, false);
        Map<String, Object> order = findOrderForUser(userId, normalizedOrderNo);
        if (order == null || order.isEmpty()) {
            throw new OrderServiceException("ORDER_NOT_FOUND", "Order does not exist.", HttpStatus.NOT_FOUND);
        }
        String orderStatus = orderStatus(order);
        if (!OrderStatus.PAID.equals(orderStatus)) {
            throw new OrderServiceException("ORDER_REFUND_UNAVAILABLE", "Only paid orders can apply for refund.", HttpStatus.CONFLICT);
        }
        String reasonCode = normalizeUserReason(request == null ? null : request.reasonCode());
        BigDecimal paidAmount = resolvePaidAmount(null, order, false);
        BigDecimal refundAmount = normalizeRefundAmount(
                request == null ? null : request.refundAmountYuan(),
                paidAmount
        );
        Map<String, Object> row = paymentRefundMapper.insertRefundIgnore(
                nextRefundNo(),
                normalizedOrderNo,
                userId,
                DEFAULT_PROVIDER,
                null,
                null,
                paidAmount,
                refundAmount,
                DEFAULT_CURRENCY,
                PaymentRefundSource.USER_APPLY,
                reasonCode,
                normalizeText(request == null ? null : request.reasonDetail(), 1000),
                orderStatus,
                OffsetDateTime.now(),
                null,
                "退款申请已提交，系统会继续处理。",
                toJson(snapshotPayload(order, null, null, DEFAULT_PROVIDER)),
                "{}",
                stableIdempotencyKey("refund:user", normalizedOrderNo, String.valueOf(userId), reasonCode)
        );
        PaymentRefundResponse response = toResponse(row);
        publishDispatchQuietly(response.refundNo(), response.status());
        return response;
    }

    public PaymentRefundPageResponse pageForUserOrder(Long userId,
                                                      String orderNo,
                                                      Integer rawPage,
                                                      Integer rawPageSize) {
        if (userId == null) {
            throw new OrderServiceException("ORDER_AUTH_REQUIRED", "Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        String normalizedOrderNo = normalizeRequiredOrderNo(orderNo, false);
        int page = normalizePage(rawPage);
        int pageSize = normalizePageSize(rawPageSize, false);
        long offset = (long) (page - 1) * pageSize;
        PaymentRefundDbPage dbPage = orderReadReplicaQueryExecutor.query(() -> new PaymentRefundDbPage(
                paymentRefundMapper.countForUserOrder(userId, normalizedOrderNo),
                paymentRefundMapper
                        .pageForUserOrder(userId, normalizedOrderNo, pageSize, offset)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        ));
        return new PaymentRefundPageResponse(page, pageSize, dbPage.total(), dbPage.records());
    }

    public PaymentRefundPageResponse pageForAdmin(Integer rawPage,
                                                  Integer rawPageSize,
                                                  String rawStatus,
                                                  String rawOrderNo,
                                                  String rawRefundNo,
                                                  String rawSource,
                                                  String rawReasonCode) {
        int page = normalizePage(rawPage);
        int pageSize = normalizePageSize(rawPageSize, true);
        long offset = (long) (page - 1) * pageSize;
        String status = normalizeOptionalStatus(rawStatus, true);
        String orderNo = normalizeOptionalOrderNo(rawOrderNo, true);
        String refundNo = normalizeOptionalRefundNo(rawRefundNo, true);
        String source = normalizeOptionalSource(rawSource);
        String reasonCode = normalizeOptionalReasonCode(rawReasonCode, true);
        PaymentRefundDbPage dbPage = orderReadReplicaQueryExecutor.query(() -> new PaymentRefundDbPage(
                paymentRefundMapper.countForAdmin(status, orderNo, refundNo, source, reasonCode),
                paymentRefundMapper
                        .pageForAdmin(status, orderNo, refundNo, source, reasonCode, pageSize, offset)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        ));
        return new PaymentRefundPageResponse(page, pageSize, dbPage.total(), dbPage.records());
    }

    public PaymentRefundResponse detailForAdmin(String refundNo) {
        String normalizedRefundNo = normalizeRequiredRefundNo(refundNo, true);
        return orderReadReplicaQueryExecutor.query(() -> {
            Map<String, Object> row = paymentRefundMapper.findByRefundNo(normalizedRefundNo);
            if (row == null || row.isEmpty()) {
            throw new AdminServiceException("ADMIN_REFUND_NOT_FOUND", "退款单不存在。", HttpStatus.NOT_FOUND);
            }
            return toResponse(row);
        });
    }

    public PaymentRefundResponse approve(String refundNo,
                                         Long version,
                                         Long adminId,
                                         String adminRemark,
                                         String userMessage) {
        String normalizedRefundNo = normalizeRequiredRefundNo(refundNo, true);
        long expectedVersion = normalizeVersion(version);
        Map<String, Object> row = paymentRefundMapper.approve(
                normalizedRefundNo,
                normalizeAdminId(adminId),
                normalizeText(adminRemark, 1000),
                normalizeText(userMessage, 1000),
                expectedVersion
        );
        return requireAdminUpdateResult(normalizedRefundNo, row);
    }

    public PaymentRefundResponse reject(String refundNo,
                                        Long version,
                                        Long adminId,
                                        String rejectReason,
                                        String adminRemark) {
        String normalizedRefundNo = normalizeRequiredRefundNo(refundNo, true);
        long expectedVersion = normalizeVersion(version);
        String normalizedRejectReason = normalizeText(rejectReason, 1000);
        if (normalizedRejectReason.isBlank()) {
            throw new AdminServiceException("ADMIN_REFUND_REJECT_REASON_INVALID", "拒绝原因不能为空。", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> row = paymentRefundMapper.reject(
                normalizedRefundNo,
                normalizeAdminId(adminId),
                normalizedRejectReason,
                normalizeText(adminRemark, 1000),
                expectedVersion
        );
        return requireAdminUpdateResult(normalizedRefundNo, row);
    }

    public PaymentRefundResponse markRefunded(String refundNo,
                                              Long version,
                                              Long adminId,
                                              String refundProofNo,
                                              String refundProofUrl,
                                              String adminRemark) {
        String normalizedRefundNo = normalizeRequiredRefundNo(refundNo, true);
        long expectedVersion = normalizeVersion(version);
        String normalizedProofNo = normalizeText(refundProofNo, 128);
        if (normalizedProofNo.isBlank()) {
            throw new AdminServiceException("ADMIN_REFUND_PROOF_NO_INVALID", "退款凭证号不能为空。", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> row = paymentRefundMapper.markRefunded(
                normalizedRefundNo,
                normalizeAdminId(adminId),
                normalizedProofNo,
                normalizeText(refundProofUrl, 2000),
                normalizeText(adminRemark, 1000),
                expectedVersion
        );
        return requireAdminUpdateResult(normalizedRefundNo, row);
    }

    PaymentRefundResponse toResponse(Map<String, Object> row) {
        return new PaymentRefundResponse(
                OrderRowMapper.text(row, "refundNo"),
                OrderRowMapper.text(row, "orderNo"),
                OrderRowMapper.longValue(row, "userId"),
                OrderRowMapper.text(row, "paymentProvider"),
                OrderRowMapper.text(row, "externalTradeNo"),
                OrderRowMapper.text(row, "externalRefundNo"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "paidAmountYuan")),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "refundAmountYuan")),
                OrderRowMapper.text(row, "currency"),
                OrderRowMapper.text(row, "status"),
                OrderRowMapper.text(row, "source"),
                OrderRowMapper.text(row, "reasonCode"),
                OrderRowMapper.text(row, "reasonDetail"),
                OrderRowMapper.text(row, "orderStatusWhenDetected"),
                OrderRowMapper.offsetDateTime(row, "detectedAt"),
                OrderRowMapper.offsetDateTime(row, "approvedAt"),
                OrderRowMapper.offsetDateTime(row, "rejectedAt"),
                OrderRowMapper.text(row, "rejectReason"),
                OrderRowMapper.offsetDateTime(row, "refundedAt"),
                OrderRowMapper.offsetDateTime(row, "refundStartedAt"),
                OrderRowMapper.intValue(row, "retryCount", 0),
                OrderRowMapper.offsetDateTime(row, "nextRetryAt"),
                OrderRowMapper.text(row, "lastErrorCode"),
                OrderRowMapper.text(row, "lastErrorMessage"),
                OrderRowMapper.text(row, "refundProofNo"),
                OrderRowMapper.text(row, "refundProofUrl"),
                OrderRowMapper.text(row, "adminRemark"),
                OrderRowMapper.text(row, "userMessage"),
                OrderRowMapper.longValue(row, "version"),
                OrderRowMapper.offsetDateTime(row, "createdAt"),
                OrderRowMapper.offsetDateTime(row, "updatedAt")
        );
    }

    private record PaymentRefundDbPage(long total, List<PaymentRefundResponse> records) {
    }

    private PaymentRefundResponse requireAdminUpdateResult(String refundNo, Map<String, Object> row) {
        if (row != null && !row.isEmpty()) {
            return toResponse(row);
        }
        Map<String, Object> existing = paymentRefundMapper.findByRefundNo(refundNo);
        if (existing == null || existing.isEmpty()) {
            throw new AdminServiceException("ADMIN_REFUND_NOT_FOUND", "退款单不存在。", HttpStatus.NOT_FOUND);
        }
        throw new AdminServiceException("ADMIN_REFUND_VERSION_CONFLICT", "退款单状态或版本已变化。", HttpStatus.CONFLICT);
    }

    private Map<String, Object> findOrderForUser(Long userId, String orderNo) {
        OrderRedisSnapshot snapshot = orderRedisSnapshotService.findSnapshotForUser(orderNo, userId).orElse(null);
        if (snapshot != null) {
            return snapshot.order();
        }
        return orderMapper.findOrderByOrderNoForUser(orderNo, userId);
    }

    private String nextRefundNo() {
        return HybridIdCodec.toBase62(hybridSemaphoreIdWorker.nextId());
    }

    private BigDecimal resolvePaidAmount(BigDecimal callbackPaidAmount,
                                         Map<String, Object> order,
                                         boolean requireWhenOrderMissing) {
        BigDecimal provided = positiveMoneyOrNull(callbackPaidAmount);
        if (provided != null) {
            return provided;
        }
        if (order != null && !order.isEmpty()) {
            return OrderAmountCalculator.money(OrderRowMapper.decimal(order, "payAmountYuan"));
        }
        if (requireWhenOrderMissing) {
            throw new OrderServiceException(
                    "ORDER_PAYMENT_AMOUNT_REQUIRED",
                    "paidAmountYuan is required when paid order cannot be found.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return BigDecimal.ZERO.setScale(2);
    }

    private BigDecimal normalizeRefundAmount(BigDecimal rawRefundAmount, BigDecimal paidAmount) {
        BigDecimal amount = rawRefundAmount == null ? paidAmount : OrderAmountCalculator.money(rawRefundAmount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(paidAmount) > 0) {
            throw new OrderServiceException("ORDER_REFUND_AMOUNT_INVALID", "Refund amount is invalid.", HttpStatus.BAD_REQUEST);
        }
        return amount;
    }

    private BigDecimal positiveMoneyOrNull(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal amount = OrderAmountCalculator.money(value);
        return amount.compareTo(BigDecimal.ZERO) > 0 ? amount : null;
    }

    private Map<String, Object> snapshotPayload(Map<String, Object> order,
                                                BigDecimal paidAmountYuan,
                                                String externalTradeNo,
                                                String paymentProvider) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order", order == null ? Map.of() : order);
        payload.put("paidAmountYuan", paidAmountYuan);
        payload.put("externalTradeNo", trimToNull(externalTradeNo));
        payload.put("paymentProvider", normalizeProvider(paymentProvider));
        return payload;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OrderServiceException("ORDER_REFUND_PAYLOAD_INVALID", "Refund payload is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void publishDispatchQuietly(String refundNo, String status) {
        if (refundNo == null || refundNo.isBlank()) {
            return;
        }
        if (!PaymentRefundStatus.REFUND_PENDING.equals(status) && !PaymentRefundStatus.REFUND_FAILED.equals(status)) {
            return;
        }
        try {
            paymentRefundMessagePublisher.publish(refundNo);
        } catch (Exception e) {
            log.warn("[Refund] dispatch message publish failed, refundNo={}", refundNo, e);
        }
    }

    private String paymentCallbackIdempotencyKey(String orderNo, String externalTradeNo) {
        String tradeNo = externalTradeNo == null || externalTradeNo.isBlank() ? "NO_EXTERNAL_TRADE" : externalTradeNo;
        return stableIdempotencyKey("refund:callback", orderNo, tradeNo);
    }

    private String stableIdempotencyKey(String prefix, String... parts) {
        StringBuilder raw = new StringBuilder(prefix == null ? "" : prefix);
        for (String part : parts) {
            raw.append(':').append(part == null ? "" : part);
        }
        UUID uuid = UUID.nameUUIDFromBytes(raw.toString().getBytes(StandardCharsets.UTF_8));
        return prefix + ":" + uuid;
    }

    private String abnormalReasonDetail(String reasonCode, String orderStatus) {
        return switch (reasonCode) {
            case PaymentRefundReasonCode.PAID_AFTER_ORDER_CLOSED -> "支付成功回调到达时订单已经关闭。";
            case PaymentRefundReasonCode.PAID_AFTER_ORDER_CANCELLED -> "支付成功回调到达时订单已经取消。";
            case PaymentRefundReasonCode.ORDER_NOT_FOUND_AFTER_PAID -> "支付成功回调到达时系统未找到对应订单。";
            default -> "支付成功回调到达时订单状态不可支付，当前状态：" + orderStatus;
        };
    }

    private String orderStatus(Map<String, Object> order) {
        if (order == null || order.isEmpty()) {
            return "NOT_FOUND";
        }
        String status = OrderRowMapper.text(order, "status");
        return status.isBlank() ? "UNKNOWN" : status;
    }

    private String normalizeUserReason(String reasonCode) {
        String value = normalizeReasonCode(reasonCode, PaymentRefundReasonCode.USER_NOT_RECEIVED_GOODS);
        if (!PaymentRefundReasonCode.USER_APPLY_ALLOWED.contains(value)) {
            throw new OrderServiceException("ORDER_REFUND_REASON_INVALID", "Refund reason is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeReasonCode(String reasonCode, String defaultValue) {
        String value = reasonCode == null ? "" : reasonCode.trim();
        if (value.isEmpty()) {
            return defaultValue;
        }
        if (!PaymentRefundReasonCode.ALL.contains(value)) {
            throw new OrderServiceException("ORDER_REFUND_REASON_INVALID", "Refund reason is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeOptionalReasonCode(String reasonCode, boolean admin) {
        String value = reasonCode == null ? "" : reasonCode.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!PaymentRefundReasonCode.ALL.contains(value)) {
            if (admin) {
                throw new AdminServiceException("ADMIN_REFUND_REASON_INVALID", "退款原因无效。", HttpStatus.BAD_REQUEST);
            }
            throw new OrderServiceException("ORDER_REFUND_REASON_INVALID", "Refund reason is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeOptionalStatus(String status, boolean admin) {
        String value = status == null ? "" : status.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!PaymentRefundStatus.ALL.contains(value)) {
            if (admin) {
                throw new AdminServiceException("ADMIN_REFUND_STATUS_INVALID", "退款状态无效。", HttpStatus.BAD_REQUEST);
            }
            throw new OrderServiceException("ORDER_REFUND_STATUS_INVALID", "Refund status is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeOptionalSource(String source) {
        String value = source == null ? "" : source.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!PaymentRefundSource.ALL.contains(value)) {
            throw new AdminServiceException("ADMIN_REFUND_SOURCE_INVALID", "退款来源无效。", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizePageSize(Integer pageSize, boolean admin) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize <= 0) {
            if (admin) {
                throw new AdminServiceException("ADMIN_REFUND_PAGE_SIZE_INVALID", "pageSize 无效。", HttpStatus.BAD_REQUEST);
            }
            throw new OrderServiceException("ORDER_REFUND_PAGE_SIZE_INVALID", "pageSize is invalid.", HttpStatus.BAD_REQUEST);
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalizeOptionalOrderNo(String orderNo, boolean admin) {
        String value = orderNo == null ? "" : orderNo.trim();
        return value.isEmpty() ? null : normalizeRequiredOrderNo(value, admin);
    }

    private String normalizeRequiredOrderNo(String orderNo, boolean admin) {
        String value = orderNo == null ? "" : orderNo.trim();
        if (value.isEmpty() || value.length() > 64 || !value.chars().allMatch(this::isBase62Char)) {
            if (admin) {
                throw new AdminServiceException("ADMIN_ORDER_NO_INVALID", "订单号无效。", HttpStatus.BAD_REQUEST);
            }
            throw new OrderServiceException("ORDER_NO_INVALID", "Order number is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeOptionalRefundNo(String refundNo, boolean admin) {
        String value = refundNo == null ? "" : refundNo.trim();
        return value.isEmpty() ? null : normalizeRequiredRefundNo(value, admin);
    }

    private String normalizeRequiredRefundNo(String refundNo, boolean admin) {
        String value = refundNo == null ? "" : refundNo.trim();
        if (value.isEmpty() || value.length() > 64 || !value.chars().allMatch(this::isBase62Char)) {
            if (admin) {
                throw new AdminServiceException("ADMIN_REFUND_NO_INVALID", "退款单号无效。", HttpStatus.BAD_REQUEST);
            }
            throw new OrderServiceException("ORDER_REFUND_NO_INVALID", "Refund number is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private boolean isBase62Char(int ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
    }

    private String normalizeProvider(String provider) {
        String value = provider == null ? "" : provider.trim();
        if (value.isEmpty()) {
            return DEFAULT_PROVIDER;
        }
        String upper = value.toUpperCase();
        return upper.length() > 32 ? upper.substring(0, 32) : upper;
    }

    private String trimToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeExternalTradeNo(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > 128 ? normalized.substring(0, 128) : normalized;
    }

    private String normalizeText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private long normalizeVersion(Long version) {
        if (version == null || version <= 0) {
            throw new AdminServiceException("ADMIN_REFUND_VERSION_INVALID", "版本号无效。", HttpStatus.BAD_REQUEST);
        }
        return version;
    }

    private Long normalizeAdminId(Long adminId) {
        return adminId == null || adminId <= 0 ? 1L : adminId;
    }
}
