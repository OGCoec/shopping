package com.example.ShoppingSystem.coupon.dto;

import java.util.List;

public record UserCouponTemplatePageResponse(int page,
                                             int pageSize,
                                             long total,
                                             List<UserCouponTemplateCardResponse> records) {
}
