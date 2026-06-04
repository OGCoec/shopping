package com.example.ShoppingSystem.coupon.dto;

import java.util.List;

public record UserCouponMinePageResponse(int page,
                                         int pageSize,
                                         long total,
                                         List<UserCouponMineCardResponse> records) {
}
