package com.example.ShoppingSystem.coupon.service;

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

    private final CouponTemplateExpireWriter couponTemplateExpireWriter;
    private final UserCouponMapper userCouponMapper;
    private final RoutedTransactionExecutor routedTransactionExecutor;

    public CouponExpireScheduler(CouponTemplateExpireWriter couponTemplateExpireWriter,
                                 UserCouponMapper userCouponMapper,
                                 RoutedTransactionExecutor routedTransactionExecutor) {
        this.couponTemplateExpireWriter = couponTemplateExpireWriter;
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
        return couponTemplateExpireWriter.expireTemplates();
    }

    private int expireUserCoupons() {
        Integer expired = routedTransactionExecutor.execute(
                DataSourceRoute.TRADE,
                userCouponMapper::expireUnusedCoupons
        );
        return expired == null ? 0 : expired;
    }
}
