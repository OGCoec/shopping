package com.example.ShoppingSystem.service.user.auth.phone.impl;

import com.example.ShoppingSystem.service.user.auth.phone.PhoneBoundCountingBloomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class PhoneBoundCountingBloomInitListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(PhoneBoundCountingBloomInitListener.class);

    private final PhoneBoundCountingBloomService phoneBoundCountingBloomService;

    public PhoneBoundCountingBloomInitListener(PhoneBoundCountingBloomService phoneBoundCountingBloomService) {
        this.phoneBoundCountingBloomService = phoneBoundCountingBloomService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            phoneBoundCountingBloomService.rebuildFromDatabase();
        } catch (RuntimeException e) {
            log.warn("Phone-bound counting bloom initialization failed, reason={}", e.getMessage());
        }
    }
}
