package com.example.ShoppingSystem.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class OrderAmountCalculator {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private OrderAmountCalculator() {
    }

    static BigDecimal lineAmount(BigDecimal price, int quantity) {
        return money(price.multiply(BigDecimal.valueOf(quantity)));
    }

    static BigDecimal discount(BigDecimal orderAmount, OrderCouponSnapshot coupon) {
        if (coupon == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal discount;
        if ("AMOUNT".equals(coupon.discountType())) {
            discount = coupon.discountAmountYuan();
        } else {
            BigDecimal rate = coupon.discountRate() == null ? ONE : coupon.discountRate();
            discount = orderAmount.multiply(ONE.subtract(rate));
            if (coupon.maxDiscountAmountYuan() != null
                    && coupon.maxDiscountAmountYuan().compareTo(BigDecimal.ZERO) >= 0
                    && discount.compareTo(coupon.maxDiscountAmountYuan()) > 0) {
                discount = coupon.maxDiscountAmountYuan();
            }
        }
        if (discount == null || discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }
        return money(discount);
    }

    static BigDecimal discount(BigDecimal orderAmount, LockedOrderCoupon coupon) {
        if (coupon == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        OrderCouponSnapshot snapshot = new OrderCouponSnapshot(
                coupon.userCouponId(),
                coupon.userCouponIdText(),
                coupon.couponTemplateId(),
                coupon.couponTemplateIdText(),
                "",
                coupon.discountType(),
                BigDecimal.ZERO,
                coupon.discountAmountYuan(),
                coupon.discountRate(),
                coupon.maxDiscountAmountYuan(),
                true,
                "UNUSED",
                "ACTIVE",
                null,
                null,
                null,
                null
        );
        return discount(orderAmount, snapshot);
    }

    static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
