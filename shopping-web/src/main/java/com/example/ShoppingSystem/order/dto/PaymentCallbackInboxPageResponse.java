package com.example.ShoppingSystem.order.dto;

import java.util.List;

public record PaymentCallbackInboxPageResponse(int page,
                                               int pageSize,
                                               long total,
                                               List<PaymentCallbackInboxResponse> records) {
}
