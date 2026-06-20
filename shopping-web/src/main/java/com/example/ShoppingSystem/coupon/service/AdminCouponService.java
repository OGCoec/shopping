package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.coupon.dto.AdminCouponClaimPageResponse;
import com.example.ShoppingSystem.coupon.dto.AdminCouponTemplatePageResponse;
import com.example.ShoppingSystem.coupon.dto.AdminCouponTemplateRequest;
import com.example.ShoppingSystem.coupon.dto.AdminCouponTemplateResponse;

public interface AdminCouponService {
    public AdminCouponTemplatePageResponse page(Integer rawPage,
                                                Integer rawPageSize,
                                                String name,
                                                String rawStatus,
                                                String rawReceiveStartAtFrom,
                                                String rawReceiveEndAtTo);

    public AdminCouponTemplateResponse detail(String rawId);

    public AdminCouponClaimPageResponse claimPage(String rawId,
                                                  Integer rawPage,
                                                  Integer rawPageSize,
                                                  String rawStatus,
                                                  String rawEmail);

    public AdminCouponTemplateResponse create(AdminCouponTemplateRequest request);

    public AdminCouponTemplateResponse update(String rawId, AdminCouponTemplateRequest request);

    public AdminCouponTemplateResponse publish(String rawId);

    public AdminCouponTemplateResponse disable(String rawId);

    public void softDelete(String rawId);
}
