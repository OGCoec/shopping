package com.example.ShoppingSystem.coupon.dto;

import java.time.OffsetDateTime;

public record AdminCouponClaimResponse(String userCouponId,
                                       String couponTemplateId,
                                       Long userId,
                                       String email,
                                       String status,
                                       OffsetDateTime receivedAt,
                                       OffsetDateTime validStartAt,
                                       OffsetDateTime validEndAt,
                                       String lockedOrderNo,
                                       OffsetDateTime lockedAt,
                                       String usedOrderNo,
                                       OffsetDateTime usedAt) {
}
