package com.example.ShoppingSystem.order.dto;

import java.util.List;

public record PaymentRefundPageResponse(int page,
                                        int pageSize,
                                        long total,
                                        List<PaymentRefundResponse> records) {
}
