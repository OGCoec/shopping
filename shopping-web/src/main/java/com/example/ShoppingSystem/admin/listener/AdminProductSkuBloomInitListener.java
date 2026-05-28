package com.example.ShoppingSystem.admin.listener;

import com.example.ShoppingSystem.admin.service.product.AdminProductSkuBloomInitializerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminProductSkuBloomInitListener implements ApplicationListener<ApplicationReadyEvent> {

    private final AdminProductSkuBloomInitializerService initializerService;

    public AdminProductSkuBloomInitListener(AdminProductSkuBloomInitializerService initializerService) {
        this.initializerService = initializerService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            initializerService.rebuildOnStartup();
        } catch (Exception e) {
            log.warn("Admin product SKU ID counting bloom initialization failed, application keeps running.", e);
        }
    }
}
