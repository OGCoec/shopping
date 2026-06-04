package com.example.ShoppingSystem.order.service;

import java.math.BigDecimal;

public record PaymentRefundDispatchItem(String refundNo,
                                        String orderNo,
                                        String paymentProvider,
                                        String externalTradeNo,
                                        BigDecimal refundAmountYuan,
                                        int retryCount) {
}
