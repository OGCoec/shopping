package com.example.ShoppingSystem.coupon.service;
import java.util.Collection;
public interface AdminCouponTemplateIndexService {

    public static final String COUPON_TEMPLATE_INDEX_ALIAS = "shopping_coupon_template";
    public void initializeOnStartup();

    public void syncCouponTemplatesAfterCommit(Collection<String> couponTemplateIds);

    public void deleteCouponTemplatesAfterCommit(Collection<String> couponTemplateIds);

    public void syncCouponTemplates(Collection<String> couponTemplateIds);

    public void deleteCouponTemplates(Collection<String> couponTemplateIds);
}
