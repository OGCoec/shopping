package com.example.ShoppingSystem.order.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentCallbackDispatchScheduler {

    private final PaymentCallbackDispatchService paymentCallbackDispatchService;
    private final PaymentCallbackDispatchProperties properties;

    public PaymentCallbackDispatchScheduler(PaymentCallbackDispatchService paymentCallbackDispatchService,
                                            PaymentCallbackDispatchProperties properties) {
        this.paymentCallbackDispatchService = paymentCallbackDispatchService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.payment-callback.dispatch.interval-millis:5000}")
    public void dispatchPendingCallbacks() {
        if (!properties.isEnabled()) {
            return;
        }
        paymentCallbackDispatchService.dispatchAvailable(properties.getBatchSize());
    }
}
