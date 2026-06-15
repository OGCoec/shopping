package com.example.ShoppingSystem.order.rabbit;

import java.util.List;

public record OrderClosingFinalizeCheckMessage(String orderNo,
                                               Long userId,
                                               Long closingDeadlineEpochMilli,
                                               List<Long> remainingDelayMillis) {

    public OrderClosingFinalizeCheckMessage {
        remainingDelayMillis = remainingDelayMillis == null
                ? List.of()
                : List.copyOf(remainingDelayMillis);
    }

    public OrderClosingFinalizeCheckMessage withRemainingDelayMillis(List<Long> delays) {
        return new OrderClosingFinalizeCheckMessage(
                orderNo,
                userId,
                closingDeadlineEpochMilli,
                delays
        );
    }
}
