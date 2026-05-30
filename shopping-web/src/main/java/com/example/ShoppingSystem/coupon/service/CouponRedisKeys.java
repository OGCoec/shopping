package com.example.ShoppingSystem.coupon.service;

public final class CouponRedisKeys {

    public static final String CLAIM_PENDING_INDEX_KEY = "shopping:coupon:claim:pending:index";
    public static final String STOCK_DIRTY_KEY = "shopping:coupon:stock:dirty";

    private CouponRedisKeys() {
    }

    public static String templateKey(String couponId) {
        return "shopping:coupon:template:" + couponId;
    }

    public static String stockKey(String couponId) {
        return "shopping:coupon:stock:" + couponId;
    }

    public static String scopeKey(String couponId) {
        return "shopping:coupon:scope:" + couponId;
    }

    public static String claimedKey(String couponId) {
        return "shopping:coupon:claimed:" + couponId;
    }

    public static String claimPendingKey(String claimId) {
        return "shopping:coupon:claim:pending:" + claimId;
    }

    public static String rebuildLockKey(String couponId) {
        return "shopping:coupon:rebuild-lock:" + couponId;
    }
}
