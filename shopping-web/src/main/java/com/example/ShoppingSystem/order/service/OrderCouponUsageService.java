package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.mapper.coupon.CouponUsageRecordMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderCouponUsageService {

    private final CouponUsageRecordMapper couponUsageRecordMapper;
    private final HybridSemaphoreIdWorker hybridSemaphoreIdWorker;

    public OrderCouponUsageService(CouponUsageRecordMapper couponUsageRecordMapper,
                                   HybridSemaphoreIdWorker hybridSemaphoreIdWorker) {
        this.couponUsageRecordMapper = couponUsageRecordMapper;
        this.hybridSemaphoreIdWorker = hybridSemaphoreIdWorker;
    }

    public void writeLock(Long userId,
                          LockedOrderCoupon coupon,
                          BigDecimal orderAmountYuan,
                          BigDecimal discountAmountYuan,
                          String orderNo) {
        if (coupon == null) {
            return;
        }
        couponUsageRecordMapper.insertUsageRecordIgnore(
                hybridSemaphoreIdWorker.nextId(),
                coupon.userCouponId(),
                coupon.couponTemplateId(),
                userId,
                orderNo,
                "LOCK",
                orderAmountYuan,
                discountAmountYuan,
                "ORDER_COUPON_LOCK:" + orderNo
        );
    }

    public void writeRelease(Long userId,
                             LockedOrderCoupon coupon,
                             String orderNo) {
        if (coupon == null) {
            return;
        }
        couponUsageRecordMapper.insertUsageRecordIgnore(
                hybridSemaphoreIdWorker.nextId(),
                coupon.userCouponId(),
                coupon.couponTemplateId(),
                userId,
                orderNo,
                "RELEASE",
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                "ORDER_COUPON_RELEASE:" + orderNo
        );
    }

    public void writeUse(Long userId,
                         LockedOrderCoupon coupon,
                         BigDecimal orderAmountYuan,
                         BigDecimal discountAmountYuan,
                         String orderNo) {
        if (coupon == null) {
            return;
        }
        couponUsageRecordMapper.insertUsageRecordIgnore(
                hybridSemaphoreIdWorker.nextId(),
                coupon.userCouponId(),
                coupon.couponTemplateId(),
                userId,
                orderNo,
                "USE",
                orderAmountYuan,
                discountAmountYuan,
                "ORDER_COUPON_USE:" + orderNo
        );
    }
}
