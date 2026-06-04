package com.example.ShoppingSystem.order.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderCouponSnapshot(byte[] userCouponId,
                                  String userCouponIdText,
                                  byte[] couponTemplateId,
                                  String couponTemplateIdText,
                                  String name,
                                  String discountType,
                                  BigDecimal thresholdAmountYuan,
                                  BigDecimal discountAmountYuan,
                                  BigDecimal discountRate,
                                  BigDecimal maxDiscountAmountYuan,
                                  boolean scopeMatched,
                                  String userCouponStatus,
                                  String templateStatus,
                                  OffsetDateTime validStartAt,
                                  OffsetDateTime validEndAt,
                                  OffsetDateTime templateValidStartAt,
                                  OffsetDateTime templateValidEndAt) {
}
