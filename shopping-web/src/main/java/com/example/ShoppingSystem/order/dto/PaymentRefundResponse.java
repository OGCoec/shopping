package com.example.ShoppingSystem.order.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentRefundResponse(String refundNo,
                                    String orderNo,
                                    Long userId,
                                    String paymentProvider,
                                    String externalTradeNo,
                                    String externalRefundNo,
                                    BigDecimal paidAmountYuan,
                                    BigDecimal refundAmountYuan,
                                    String currency,
                                    String status,
                                    String source,
                                    String reasonCode,
                                    String reasonDetail,
                                    String orderStatusWhenDetected,
                                    OffsetDateTime detectedAt,
                                    OffsetDateTime approvedAt,
                                    OffsetDateTime rejectedAt,
                                    String rejectReason,
                                    OffsetDateTime refundedAt,
                                    OffsetDateTime refundStartedAt,
                                    Integer retryCount,
                                    OffsetDateTime nextRetryAt,
                                    String lastErrorCode,
                                    String lastErrorMessage,
                                    String refundProofNo,
                                    String refundProofUrl,
                                    String adminRemark,
                                    String userMessage,
                                    Long version,
                                    OffsetDateTime createdAt,
                                    OffsetDateTime updatedAt) {
}
