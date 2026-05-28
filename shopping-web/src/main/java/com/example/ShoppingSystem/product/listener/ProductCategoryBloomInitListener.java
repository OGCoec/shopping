package com.example.ShoppingSystem.product.listener;

import com.example.ShoppingSystem.product.service.ProductCategoryBloomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductCategoryBloomInitListener implements ApplicationListener<ApplicationReadyEvent> {

    private final ProductCategoryBloomService categoryBloomService;

    public ProductCategoryBloomInitListener(ProductCategoryBloomService categoryBloomService) {
        this.categoryBloomService = categoryBloomService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            categoryBloomService.rebuildOnStartup();
        } catch (Exception e) {
            log.warn("Product category ID counting bloom initialization failed, application keeps running.", e);
        }
    }
}
