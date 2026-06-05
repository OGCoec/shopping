package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.mapper.order.PaymentCallbackInboxMapper;
import com.example.ShoppingSystem.order.dto.OrderPaymentCallbackReceivedResponse;
import com.example.ShoppingSystem.order.dto.OrderPaymentCallbackRequest;
import com.example.ShoppingSystem.order.rabbit.PaymentCallbackMessagePublisher;
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
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentCallbackReceiveService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackReceiveService.class);

    private static final String DEFAULT_PROVIDER = "SIMULATED";

    private final PaymentCallbackInboxMapper paymentCallbackInboxMapper;
    private final PaymentCallbackMessagePublisher paymentCallbackMessagePublisher;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;
    private final ObjectMapper objectMapper;

    public PaymentCallbackReceiveService(PaymentCallbackInboxMapper paymentCallbackInboxMapper,
                                         PaymentCallbackMessagePublisher paymentCallbackMessagePublisher,
                                         HybridSemaphoreIdWorker hybridSemaphoreIdWorker,
                                         ObjectMapper objectMapper) {
        this.paymentCallbackInboxMapper = paymentCallbackInboxMapper;
        this.paymentCallbackMessagePublisher = paymentCallbackMessagePublisher;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
        this.objectMapper = objectMapper;
    }

    public OrderPaymentCallbackReceivedResponse receive(OrderPaymentCallbackRequest request) {
        String orderNo = normalizeOrderNo(request == null ? null : request.orderNo());
        String externalTradeNo = normalizeExternalTradeNo(request == null ? null : request.externalTradeNo(), orderNo);
        String paymentProvider = normalizeProvider(request == null ? null : request.paymentProvider());
        OffsetDateTime paidAt = request == null || request.paidAt() == null ? OffsetDateTime.now() : request.paidAt();
        BigDecimal paidAmount = normalizePaidAmount(request == null ? null : request.paidAmountYuan());
        String idempotencyKey = stableIdempotencyKey("payment:callback", orderNo, externalTradeNo);
        Map<String, Object> row = paymentCallbackInboxMapper.upsertCallbackIdempotent(
                nextCallbackNo(),
                orderNo,
                externalTradeNo,
                paymentProvider,
                paidAt,
                paidAmount,
                idempotencyKey,
                rawPayloadJson(request, orderNo, externalTradeNo, paymentProvider, paidAt, paidAmount)
        );
        if (row == null || row.isEmpty()) {
            throw new OrderServiceException("ORDER_PAYMENT_CALLBACK_RECEIVE_FAILED", "Payment callback receive failed.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        publishDispatchQuietly(OrderRowMapper.text(row, "callbackNo"), OrderRowMapper.text(row, "status"));
        return new OrderPaymentCallbackReceivedResponse(
                OrderRowMapper.text(row, "callbackNo"),
                OrderRowMapper.text(row, "orderNo"),
                OrderRowMapper.text(row, "externalTradeNo"),
                OrderRowMapper.text(row, "status")
        );
    }

    private void publishDispatchQuietly(String callbackNo, String status) {
        if (callbackNo == null || callbackNo.isBlank()) {
            return;
        }
        if (!PaymentCallbackInboxStatus.RECEIVED.equals(status) && !PaymentCallbackInboxStatus.FAILED.equals(status)) {
            return;
        }
        try {
            paymentCallbackMessagePublisher.publish(callbackNo);
        } catch (Exception e) {
            log.warn("[PaymentCallback] dispatch message publish failed, callbackNo={}", callbackNo, e);
        }
    }

    private String rawPayloadJson(OrderPaymentCallbackRequest request,
                                  String orderNo,
                                  String externalTradeNo,
                                  String paymentProvider,
                                  OffsetDateTime paidAt,
                                  BigDecimal paidAmount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request", request == null ? Map.of() : request);
        payload.put("orderNo", orderNo);
        payload.put("externalTradeNo", externalTradeNo);
        payload.put("paymentProvider", paymentProvider);
        payload.put("paidAt", paidAt.toString());
        payload.put("paidAmountYuan", paidAmount);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new OrderServiceException("ORDER_PAYMENT_CALLBACK_PAYLOAD_INVALID", "Payment callback payload is invalid.", HttpStatus.BAD_REQUEST);
        }
    }

    private String nextCallbackNo() {
        return HybridIdCodec.toBase62(hybridSemaphoreIdWorker.nextId());
    }

    private String normalizeOrderNo(String orderNo) {
        String value = orderNo == null ? "" : orderNo.trim();
        if (value.isEmpty() || value.length() > 64 || !value.chars().allMatch(this::isBase62Char)) {
            throw new OrderServiceException("ORDER_NO_INVALID", "Order number is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeExternalTradeNo(String rawExternalTradeNo, String orderNo) {
        String value = rawExternalTradeNo == null ? "" : rawExternalTradeNo.trim();
        if (value.isEmpty()) {
            value = "MOCKCALLBACK-" + orderNo;
        }
        return value.length() > 128 ? value.substring(0, 128) : value;
    }

    private String normalizeProvider(String provider) {
        String value = provider == null ? "" : provider.trim();
        if (value.isEmpty()) {
            return DEFAULT_PROVIDER;
        }
        String upper = value.toUpperCase();
        return upper.length() > 32 ? upper.substring(0, 32) : upper;
    }

    private BigDecimal normalizePaidAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        BigDecimal money = OrderAmountCalculator.money(amount);
        if (money.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderServiceException("ORDER_PAYMENT_AMOUNT_INVALID", "paidAmountYuan is invalid.", HttpStatus.BAD_REQUEST);
        }
        return money;
    }

    private boolean isBase62Char(int ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
    }

    private String stableIdempotencyKey(String prefix, String... parts) {
        StringBuilder raw = new StringBuilder(prefix == null ? "" : prefix);
        for (String part : parts) {
            raw.append(':').append(part == null ? "" : part);
        }
        UUID uuid = UUID.nameUUIDFromBytes(raw.toString().getBytes(StandardCharsets.UTF_8));
        return prefix + ":" + uuid;
    }
}
