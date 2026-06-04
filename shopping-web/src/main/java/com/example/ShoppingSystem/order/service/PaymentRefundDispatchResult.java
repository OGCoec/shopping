package com.example.ShoppingSystem.order.service;

import java.time.OffsetDateTime;

public record PaymentRefundDispatchResult(String refundNo,
                                          String status,
                                          String externalRefundNo,
                                          String lastErrorCode,
                                          String lastErrorMessage,
                                          int retryCount,
                                          OffsetDateTime nextRetryAt,
                                          OffsetDateTime refundedAt) {
}
