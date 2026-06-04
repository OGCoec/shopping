package com.example.ShoppingSystem.order.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentCallbackInboxResponse(String callbackNo,
                                           String orderNo,
                                           String externalTradeNo,
                                           String paymentProvider,
                                           OffsetDateTime paidAt,
                                           BigDecimal paidAmountYuan,
                                           String status,
                                           int retryCount,
                                           OffsetDateTime nextRetryAt,
                                           String resultOutcome,
                                           String resultOrderStatus,
                                           String refundNo,
                                           String lastErrorCode,
                                           String lastErrorMessage,
                                           String rawPayloadJson,
                                           Long version,
                                           OffsetDateTime createdAt,
                                           OffsetDateTime updatedAt) {
}
