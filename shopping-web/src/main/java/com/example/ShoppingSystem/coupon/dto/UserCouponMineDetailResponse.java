package com.example.ShoppingSystem.coupon.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record UserCouponMineDetailResponse(String userCouponId,
                                           String couponTemplateId,
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
                                           String templateStatus,
                                           String status,
                                           OffsetDateTime receivedAt,
                                           OffsetDateTime validStartAt,
                                           OffsetDateTime validEndAt,
                                           String lockedOrderNo,
                                           OffsetDateTime lockedAt,
                                           String usedOrderNo,
                                           OffsetDateTime usedAt) {
}
