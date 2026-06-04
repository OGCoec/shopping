package com.example.ShoppingSystem.order.service;

import java.math.BigDecimal;

public record LockedOrderCoupon(byte[] userCouponId,
                                String userCouponIdText,
                                byte[] couponTemplateId,
                                String couponTemplateIdText,
                                String discountType,
                                BigDecimal discountAmountYuan,
                                BigDecimal discountRate,
                                BigDecimal maxDiscountAmountYuan) {
}
