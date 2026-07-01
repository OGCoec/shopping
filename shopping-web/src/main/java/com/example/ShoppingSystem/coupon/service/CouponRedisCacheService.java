package com.example.ShoppingSystem.coupon.service;

public interface CouponRedisCacheService {
    public boolean writeCouponToRedis(byte[] couponTemplateId);

    public void markDisabled(String couponId);

    public void deleteCouponRuntime(String couponId);
}
