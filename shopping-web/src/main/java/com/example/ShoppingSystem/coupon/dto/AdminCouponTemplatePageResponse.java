package com.example.ShoppingSystem.coupon.dto;

import java.util.List;

public record AdminCouponTemplatePageResponse(int page,
                                              int pageSize,
                                              long total,
                                              List<AdminCouponTemplateResponse> records) {
}
