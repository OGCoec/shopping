package com.example.ShoppingSystem.coupon.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminCouponTemplateRequest(String couponCode,
                                         String name,
                                         String discountType,
                                         BigDecimal thresholdAmountYuan,
                                         BigDecimal discountAmountYuan,
                                         BigDecimal discountRate,
                                         BigDecimal maxDiscountAmountYuan,
                                         Integer totalQuantity,
                                         Integer perUserLimit,
                                         String scopeType,
                                         List<String> targetIds,
                                         String receiveStartAt,
                                         String receiveEndAt,
                                         String validStartAt,
                                         String validEndAt) {
}
