package com.example.ShoppingSystem.coupon.dto;

import java.util.List;

public record AdminCouponClaimPageResponse(int page,
                                           int pageSize,
                                           long total,
                                           List<AdminCouponClaimResponse> records) {
}
