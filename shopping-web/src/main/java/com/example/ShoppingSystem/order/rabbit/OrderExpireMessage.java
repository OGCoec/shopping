package com.example.ShoppingSystem.order.rabbit;

public record OrderExpireMessage(String orderNo,
                                 Long userId,
                                 Long expireAtEpochMilli,
                                 String phase) {

    public static final String PHASE_PAYMENT_EXPIRE = "PAYMENT_EXPIRE";
    public static final String PHASE_CLOSING_FINALIZE = "CLOSING_FINALIZE";

    public OrderExpireMessage {
        phase = normalizePhase(phase);
    }

    public static OrderExpireMessage paymentExpire(String orderNo, Long userId, Long expireAtEpochMilli) {
        return new OrderExpireMessage(orderNo, userId, expireAtEpochMilli, PHASE_PAYMENT_EXPIRE);
    }

    public static OrderExpireMessage closingFinalize(String orderNo, Long userId, Long closingDeadlineEpochMilli) {
        return new OrderExpireMessage(orderNo, userId, closingDeadlineEpochMilli, PHASE_CLOSING_FINALIZE);
    }

    public static String normalizePhase(String rawPhase) {
        String value = rawPhase == null ? "" : rawPhase.trim();
        if (value.isEmpty()) {
            return PHASE_PAYMENT_EXPIRE;
        }
        if (PHASE_CLOSING_FINALIZE.equals(value)) {
            return PHASE_CLOSING_FINALIZE;
        }
        return PHASE_PAYMENT_EXPIRE;
    }
}
