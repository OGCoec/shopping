package com.example.ShoppingSystem.admin.listener;

import com.example.ShoppingSystem.admin.service.product.AdminProductSpuIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminProductSpuIndexInitListener implements ApplicationListener<ApplicationReadyEvent> {

    private final AdminProductSpuIndexService indexService;

    public AdminProductSpuIndexInitListener(AdminProductSpuIndexService indexService) {
        this.indexService = indexService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            indexService.initializeOnStartup();
        } catch (Exception e) {
            log.warn("Admin product SPU Elasticsearch index initialization failed, application keeps running.", e);
        }
    }
}
