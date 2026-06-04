package com.example.ShoppingSystem.order.dto;

import java.math.BigDecimal;

public record OrderCouponOptionResponse(String userCouponId,
                                        String couponTemplateId,
                                        String name,
                                        BigDecimal discountAmountYuan,
                                        boolean selected,
                                        String reason) {
}
