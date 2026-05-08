package com.example.ShoppingSystem.service.user.auth.phone.impl;

import com.example.ShoppingSystem.service.user.auth.phone.PhoneVerifiedUserBloomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class PhoneVerifiedUserBloomInitListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerifiedUserBloomInitListener.class);

    private final PhoneVerifiedUserBloomService phoneVerifiedUserBloomService;

    public PhoneVerifiedUserBloomInitListener(PhoneVerifiedUserBloomService phoneVerifiedUserBloomService) {
        this.phoneVerifiedUserBloomService = phoneVerifiedUserBloomService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            phoneVerifiedUserBloomService.rebuildFromDatabase();
        } catch (RuntimeException e) {
            log.warn("Phone-verified-user counting bloom initialization failed, reason={}", e.getMessage());
        }
    }
}
