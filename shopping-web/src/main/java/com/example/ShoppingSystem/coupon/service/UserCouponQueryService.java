package com.example.ShoppingSystem.coupon.service;
import com.example.ShoppingSystem.coupon.dto.UserCouponMineDetailResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponMinePageResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplateDetailResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplatePageResponse;
public interface UserCouponQueryService {
    public UserCouponTemplatePageResponse receivablePage(Long userId,
                                                         Integer rawPage,
                                                         Integer rawPageSize,
                                                         String rawName);

    public UserCouponTemplateDetailResponse receivableDetail(Long userId, String rawCouponTemplateId);

    public UserCouponMinePageResponse minePage(Long userId,
                                               Integer rawPage,
                                               Integer rawPageSize,
                                               String rawStatus);

    public UserCouponMineDetailResponse mineDetail(Long userId, String rawUserCouponId);
}
