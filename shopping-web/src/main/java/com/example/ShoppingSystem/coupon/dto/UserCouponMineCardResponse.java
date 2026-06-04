package com.example.ShoppingSystem.coupon.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UserCouponMineCardResponse(String userCouponId,
                                         String couponTemplateId,
                                         String couponCode,
                                         String name,
                                         String discountType,
                                         BigDecimal thresholdAmountYuan,
                                         BigDecimal discountAmountYuan,
                                         BigDecimal discountRate,
                                         BigDecimal maxDiscountAmountYuan,
                                         String status,
                                         OffsetDateTime receivedAt,
                                         OffsetDateTime validStartAt,
                                         OffsetDateTime validEndAt,
                                         OffsetDateTime usedAt) {
}
