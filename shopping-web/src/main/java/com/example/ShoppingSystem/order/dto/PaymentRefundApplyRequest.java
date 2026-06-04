package com.example.ShoppingSystem.order.dto;

import java.math.BigDecimal;

public record PaymentRefundApplyRequest(String reasonCode,
                                        String reasonDetail,
                                        BigDecimal refundAmountYuan) {
}
