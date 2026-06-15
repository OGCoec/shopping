package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CouponExpireScheduler {

    private static final Logger log = LoggerFactory.getLogger(CouponExpireScheduler.class);

    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;
    private final TransactionTemplate transactionTemplate;

    public CouponExpireScheduler(CouponTemplateMapper couponTemplateMapper,
                                 UserCouponMapper userCouponMapper,
                                 TransactionTemplate transactionTemplate) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.userCouponMapper = userCouponMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${shopping.coupon.expire-check-delay-ms:60000}")
    public void expireCoupons() {
        ExpireCounts counts = transactionTemplate.execute(status -> new ExpireCounts(
                couponTemplateMapper.expireTemplates(),
                userCouponMapper.expireUnusedCoupons()
        ));
        if (counts != null && (counts.expiredTemplates() > 0 || counts.expiredUserCoupons() > 0)) {
            log.info("[Coupon] expire sweep finished, templates={}, userCoupons={}",
                    counts.expiredTemplates(), counts.expiredUserCoupons());
        }
    }

    private record ExpireCounts(int expiredTemplates, int expiredUserCoupons) {
    }
}
