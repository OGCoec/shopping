package com.example.ShoppingSystem.outbox.couponexpire;

public final class CouponTemplatesExpiredRouting {

    private CouponTemplatesExpiredRouting() {
    }

    public static final String EVENT_TYPE = "COUPON_TEMPLATES_EXPIRED";
    public static final String AGGREGATE_TYPE = "COUPON_TEMPLATE";

    public static final String EXCHANGE = "coupon.templates.expired.exchange";
    public static final String QUEUE = "coupon.templates.expired.trade.queue";
    public static final String DEAD_LETTER_QUEUE = "coupon.templates.expired.trade.dlq";

    public static final String ROUTING_KEY = "coupon.templates.expired.trade";
    public static final String DEAD_ROUTING_KEY = "coupon.templates.expired.trade.dead";
}
