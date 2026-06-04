package com.example.ShoppingSystem.coupon.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record UserCouponTemplateDetailResponse(String couponTemplateId,
                                               String couponCode,
                                               String name,
                                               String discountType,
                                               BigDecimal thresholdAmountYuan,
                                               BigDecimal discountAmountYuan,
                                               BigDecimal discountRate,
                                               BigDecimal maxDiscountAmountYuan,
                                               Integer totalQuantity,
                                               Integer remainingQuantity,
                                               Integer perUserLimit,
                                               String scopeType,
                                               List<String> targetIds,
                                               OffsetDateTime receiveStartAt,
                                               OffsetDateTime receiveEndAt,
                                               OffsetDateTime validStartAt,
                                               OffsetDateTime validEndAt,
                                               boolean claimed,
                                               boolean canClaim,
                                               String userCouponId,
                                               String userCouponStatus) {
}
