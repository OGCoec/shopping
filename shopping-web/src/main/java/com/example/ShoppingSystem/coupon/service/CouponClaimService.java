package com.example.ShoppingSystem.coupon.service;
import com.example.ShoppingSystem.coupon.dto.CouponClaimResponse;
public interface CouponClaimService {
    public CouponClaimResponse claim(String rawCouponTemplateId, Long userId);
}
