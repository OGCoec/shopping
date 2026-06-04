package com.example.ShoppingSystem.order.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentRefundDispatchScheduler {

    private final PaymentRefundDispatchService paymentRefundDispatchService;
    private final PaymentRefundDispatchProperties properties;

    public PaymentRefundDispatchScheduler(PaymentRefundDispatchService paymentRefundDispatchService,
                                          PaymentRefundDispatchProperties properties) {
        this.paymentRefundDispatchService = paymentRefundDispatchService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.refund.dispatch.interval-millis:5000}")
    public void dispatchPendingRefunds() {
        if (!properties.isEnabled()) {
            return;
        }
        paymentRefundDispatchService.dispatchAvailable(properties.getBatchSize());
    }
}
