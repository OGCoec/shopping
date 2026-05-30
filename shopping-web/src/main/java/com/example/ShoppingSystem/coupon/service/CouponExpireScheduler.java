package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CouponExpireScheduler {

    private static final Logger log = LoggerFactory.getLogger(CouponExpireScheduler.class);

    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;

    public CouponExpireScheduler(CouponTemplateMapper couponTemplateMapper,
                                 UserCouponMapper userCouponMapper) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.userCouponMapper = userCouponMapper;
    }

    @Scheduled(fixedDelayString = "${shopping.coupon.expire-check-delay-ms:60000}")
    public void expireCoupons() {
        int expiredTemplates = couponTemplateMapper.expireTemplates();
        int expiredUserCoupons = userCouponMapper.expireUnusedCoupons();
        if (expiredTemplates > 0 || expiredUserCoupons > 0) {
            log.info("[Coupon] expire sweep finished, templates={}, userCoupons={}",
                    expiredTemplates, expiredUserCoupons);
        }
    }
}
