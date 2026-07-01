package com.example.ShoppingSystem.order.service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface OrderCouponUsageService {
    public void writeLock(Long userId,
                          LockedOrderCoupon coupon,
                          BigDecimal orderAmountYuan,
                          BigDecimal discountAmountYuan,
                          String orderNo);

    public void writeReleases(List<Map<String, Object>> releasedCoupons);

    public void writeRelease(Long userId,
                             LockedOrderCoupon coupon,
                             String orderNo);

    public void writeUse(Long userId,
                         LockedOrderCoupon coupon,
                         BigDecimal orderAmountYuan,
                         BigDecimal discountAmountYuan,
                         String orderNo);
}
