package com.example.ShoppingSystem.coupon.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminCouponTemplateResponse(String id,
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
                                          String status,
                                          Long version,
                                          OffsetDateTime createdAt,
                                          OffsetDateTime updatedAt) {
}
