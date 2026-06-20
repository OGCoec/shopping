package com.example.ShoppingSystem.order.service.impl.PaymentStatusQueryService;

import org.springframework.stereotype.Service;
import com.example.ShoppingSystem.order.service.PaymentStatusQueryResult;
import com.example.ShoppingSystem.order.service.PaymentStatusQueryService;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Service
// @ConditionalOnMissingBean(PaymentStatusQueryService.class)
public class SimulatedPaymentStatusQueryService implements PaymentStatusQueryService {

    @Override
    public PaymentStatusQueryResult query(String orderNo, Long userId) {
        return PaymentStatusQueryResult.unknown();
    }
}
