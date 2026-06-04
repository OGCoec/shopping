package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.config.datasource.OrderReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.order.PaymentCallbackInboxMapper;
import com.example.ShoppingSystem.order.dto.PaymentCallbackInboxPageResponse;
import com.example.ShoppingSystem.order.dto.PaymentCallbackInboxResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PaymentCallbackInboxQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentCallbackInboxMapper paymentCallbackInboxMapper;
    private final OrderReadReplicaQueryExecutor orderReadReplicaQueryExecutor;

    public PaymentCallbackInboxQueryService(PaymentCallbackInboxMapper paymentCallbackInboxMapper,
                                            OrderReadReplicaQueryExecutor orderReadReplicaQueryExecutor) {
        this.paymentCallbackInboxMapper = paymentCallbackInboxMapper;
        this.orderReadReplicaQueryExecutor = orderReadReplicaQueryExecutor;
    }

    public PaymentCallbackInboxPageResponse page(Integer rawPage,
                                                 Integer rawPageSize,
                                                 String rawStatus,
                                                 String rawOrderNo,
                                                 String rawCallbackNo,
                                                 String rawExternalTradeNo,
                                                 String rawResultOutcome) {
        int page = normalizePage(rawPage);
        int pageSize = normalizePageSize(rawPageSize);
        long offset = (long) (page - 1) * pageSize;
        String status = normalizeOptionalStatus(rawStatus);
        String orderNo = normalizeOptionalBase62(rawOrderNo, "ADMIN_ORDER_NO_INVALID", "订单号无效。");
        String callbackNo = normalizeOptionalBase62(rawCallbackNo, "ADMIN_PAYMENT_CALLBACK_NO_INVALID", "支付回调流水号无效。");
        String externalTradeNo = normalizeOptionalText(rawExternalTradeNo, 128);
        String resultOutcome = normalizeOptionalOutcome(rawResultOutcome);
        CallbackDbPage dbPage = orderReadReplicaQueryExecutor.query(() -> new CallbackDbPage(
                paymentCallbackInboxMapper.countForAdmin(status, orderNo, callbackNo, externalTradeNo, resultOutcome),
                paymentCallbackInboxMapper
                        .pageForAdmin(status, orderNo, callbackNo, externalTradeNo, resultOutcome, pageSize, offset)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        ));
        return new PaymentCallbackInboxPageResponse(page, pageSize, dbPage.total(), dbPage.records());
    }

    public PaymentCallbackInboxResponse detail(String callbackNo) {
        String normalized = normalizeRequiredBase62(callbackNo, "ADMIN_PAYMENT_CALLBACK_NO_INVALID", "支付回调流水号无效。");
        return orderReadReplicaQueryExecutor.query(() -> {
            Map<String, Object> row = paymentCallbackInboxMapper.findByCallbackNo(normalized);
            if (row == null || row.isEmpty()) {
                throw new AdminServiceException("ADMIN_PAYMENT_CALLBACK_NOT_FOUND", "支付回调不存在。", HttpStatus.NOT_FOUND);
            }
            return toResponse(row);
        });
    }

    private PaymentCallbackInboxResponse toResponse(Map<String, Object> row) {
        return new PaymentCallbackInboxResponse(
                OrderRowMapper.text(row, "callbackNo"),
                OrderRowMapper.text(row, "orderNo"),
                OrderRowMapper.text(row, "externalTradeNo"),
                OrderRowMapper.text(row, "paymentProvider"),
                OrderRowMapper.offsetDateTime(row, "paidAt"),
                OrderAmountCalculator.money(OrderRowMapper.nullableDecimal(row, "paidAmountYuan")),
                OrderRowMapper.text(row, "status"),
                OrderRowMapper.intValue(row, "retryCount", 0),
                OrderRowMapper.offsetDateTime(row, "nextRetryAt"),
                OrderRowMapper.text(row, "resultOutcome"),
                OrderRowMapper.text(row, "resultOrderStatus"),
                OrderRowMapper.text(row, "refundNo"),
                OrderRowMapper.text(row, "lastErrorCode"),
                OrderRowMapper.text(row, "lastErrorMessage"),
                OrderRowMapper.text(row, "rawPayloadJson"),
                OrderRowMapper.longValue(row, "version"),
                OrderRowMapper.offsetDateTime(row, "createdAt"),
                OrderRowMapper.offsetDateTime(row, "updatedAt")
        );
    }

    private String normalizeOptionalStatus(String status) {
        String value = normalizeOptionalText(status, 32);
        if (value == null) {
            return null;
        }
        if (!PaymentCallbackInboxStatus.ALL.contains(value)) {
            throw new AdminServiceException("ADMIN_PAYMENT_CALLBACK_STATUS_INVALID", "支付回调状态无效。", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeOptionalOutcome(String outcome) {
        String value = normalizeOptionalText(outcome, 32);
        if (value == null) {
            return null;
        }
        if (!PaymentCallbackOutcome.ALL.contains(value)) {
            throw new AdminServiceException("ADMIN_PAYMENT_CALLBACK_OUTCOME_INVALID", "支付回调结果无效。", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeOptionalBase62(String value, String code, String message) {
        String normalized = normalizeOptionalText(value, 64);
        return normalized == null ? null : normalizeRequiredBase62(normalized, code, message);
    }

    private String normalizeRequiredBase62(String value, String code, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 64 || !normalized.chars().allMatch(this::isBase62Char)) {
            throw new AdminServiceException(code, message, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize <= 0) {
            throw new AdminServiceException("ADMIN_PAYMENT_CALLBACK_PAGE_SIZE_INVALID", "pageSize 无效。", HttpStatus.BAD_REQUEST);
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private boolean isBase62Char(int ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
    }

    private record CallbackDbPage(long total, List<PaymentCallbackInboxResponse> records) {
    }
}
