package com.example.ShoppingSystem.admin.listener;

import com.example.ShoppingSystem.coupon.service.AdminCouponTemplateIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminCouponTemplateIndexInitListener implements ApplicationListener<ApplicationReadyEvent> {

    private final AdminCouponTemplateIndexService indexService;

    public AdminCouponTemplateIndexInitListener(AdminCouponTemplateIndexService indexService) {
        this.indexService = indexService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            indexService.initializeOnStartup();
        } catch (Exception e) {
            log.warn("Admin coupon template Elasticsearch index initialization failed, application keeps running.", e);
        }
    }
}
