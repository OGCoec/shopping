package com.example.ShoppingSystem.order.rabbit;

import java.util.List;

public record OrderPaymentExpireCheckMessage(String orderNo,
                                             Long userId,
                                             Long expireAtEpochMilli,
                                             List<Long> remainingDelayMillis) {

    public OrderPaymentExpireCheckMessage {
        remainingDelayMillis = remainingDelayMillis == null
                ? List.of()
                : remainingDelayMillis.stream()
                .filter(value -> value != null && value > 0)
                .map(value -> Math.max(1L, value))
                .toList();
    }

    public OrderPaymentExpireCheckMessage withRemainingDelayMillis(List<Long> delays) {
        return new OrderPaymentExpireCheckMessage(orderNo, userId, expireAtEpochMilli, delays);
    }
}
