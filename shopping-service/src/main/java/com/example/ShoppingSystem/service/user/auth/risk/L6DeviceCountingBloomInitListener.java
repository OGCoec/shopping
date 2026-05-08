package com.example.ShoppingSystem.service.user.auth.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Application-ready listener for L6 device counting bloom initialization.
 */
@Component
public class L6DeviceCountingBloomInitListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(L6DeviceCountingBloomInitListener.class);

    private final L6DeviceCountingBloomInitializerService initializerService;

    public L6DeviceCountingBloomInitListener(L6DeviceCountingBloomInitializerService initializerService) {
        this.initializerService = initializerService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        long start = System.currentTimeMillis();
        try {
            initializerService.rebuildL6FilterOnStartup();
            log.info("L6 device counting bloom startup initialization completed, cost={}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("L6 device counting bloom startup initialization failed, application continues", e);
        }
    }
}
