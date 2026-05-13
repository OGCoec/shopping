package com.example.ShoppingSystem.service.user.auth.risk.impl;

import com.example.ShoppingSystem.service.user.auth.risk.TerminatedAccountEmailBloomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class TerminatedAccountEmailCountingBloomInitListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(TerminatedAccountEmailCountingBloomInitListener.class);

    private final TerminatedAccountEmailBloomService terminatedAccountEmailBloomService;

    public TerminatedAccountEmailCountingBloomInitListener(TerminatedAccountEmailBloomService terminatedAccountEmailBloomService) {
        this.terminatedAccountEmailBloomService = terminatedAccountEmailBloomService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            terminatedAccountEmailBloomService.rebuildFromDatabase();
        } catch (RuntimeException e) {
            log.warn("Terminated account email counting bloom initialization failed, reason={}", e.getMessage());
        }
    }
}
