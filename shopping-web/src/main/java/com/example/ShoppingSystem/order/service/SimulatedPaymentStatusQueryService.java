package com.example.ShoppingSystem.order.service;

import org.springframework.stereotype.Service;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Service
// @ConditionalOnMissingBean(PaymentStatusQueryService.class)
public class SimulatedPaymentStatusQueryService implements PaymentStatusQueryService {

    @Override
    public PaymentStatusQueryResult query(String orderNo, Long userId) {
        return PaymentStatusQueryResult.unknown();
    }
}
