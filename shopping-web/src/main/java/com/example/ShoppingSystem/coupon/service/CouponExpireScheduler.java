package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutedTransactionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CouponExpireScheduler {

    private static final Logger log = LoggerFactory.getLogger(CouponExpireScheduler.class);

    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;
    private final RoutedTransactionExecutor routedTransactionExecutor;

    public CouponExpireScheduler(CouponTemplateMapper couponTemplateMapper,
                                 UserCouponMapper userCouponMapper,
                                 RoutedTransactionExecutor routedTransactionExecutor) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.userCouponMapper = userCouponMapper;
        this.routedTransactionExecutor = routedTransactionExecutor;
    }

    @Scheduled(fixedDelayString = "${shopping.coupon.expire-check-delay-ms:60000}")
    public void expireCoupons() {
        int expiredTemplates = expireTemplates();
        int expiredUserCoupons = expireUserCoupons();
        if (expiredTemplates > 0 || expiredUserCoupons > 0) {
            log.info("[Coupon] expire sweep finished, templates={}, userCoupons={}",
                    expiredTemplates, expiredUserCoupons);
        }
    }

    private int expireTemplates() {
        Integer expired = routedTransactionExecutor.execute(
                DataSourceRoute.COUPON,
                couponTemplateMapper::expireTemplates
        );
        return expired == null ? 0 : expired;
    }

    private int expireUserCoupons() {
        Integer expired = routedTransactionExecutor.execute(
                DataSourceRoute.TRADE,
                userCouponMapper::expireUnusedCoupons
        );
        return expired == null ? 0 : expired;
    }
}
