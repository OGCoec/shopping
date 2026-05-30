package com.example.ShoppingSystem.coupon.dto;

public record CouponClaimResponse(boolean success,
                                  String code,
                                  String message,
                                  String userCouponId) {

    public static CouponClaimResponse ok(String userCouponId) {
        return new CouponClaimResponse(true, "COUPON_CLAIM_OK", "ok", userCouponId);
    }

    public static CouponClaimResponse fail(String code, String message) {
        return new CouponClaimResponse(false, code, message, null);
    }
}
