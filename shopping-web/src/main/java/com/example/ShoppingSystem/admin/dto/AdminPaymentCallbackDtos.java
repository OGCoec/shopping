package com.example.ShoppingSystem.admin.dto;

public final class AdminPaymentCallbackDtos {

    private AdminPaymentCallbackDtos() {
    }

    public record AdminPaymentCallbackDispatchResponse(int claimedCount,
                                                       int inboxWrittenCount,
                                                       int refundCount,
                                                       int failedCount) {
    }
}
