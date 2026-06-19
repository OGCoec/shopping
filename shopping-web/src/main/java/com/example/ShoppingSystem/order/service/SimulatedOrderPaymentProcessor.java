package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.dto.OrderPaymentRequest;
import com.example.ShoppingSystem.order.dto.OrderPaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class SimulatedOrderPaymentProcessor implements OrderPaymentProcessor {

    private final OrderPaymentSuccessService orderPaymentSuccessService;

    public SimulatedOrderPaymentProcessor(OrderPaymentSuccessService orderPaymentSuccessService) {
        this.orderPaymentSuccessService = orderPaymentSuccessService;
    }

    @Override
    public String paymentType() {
        return OrderPaymentType.SIMULATED;
    }

    @Override
    public OrderPaymentResponse pay(Long userId, String orderNo, OrderPaymentRequest request) {
        OffsetDateTime paidAt = OffsetDateTime.now();
        String externalTradeNo = externalTradeNo(request == null ? null : request.externalTradeNo(), orderNo);
        boolean paid = orderPaymentSuccessService.markPendingPaidForUser(
                userId,
                orderNo,
                paidAt,
                externalTradeNo
        );
        if (!paid) {
            throw new OrderServiceException("ORDER_PAY_UNAVAILABLE", "Only pending current-user orders can be paid.", HttpStatus.CONFLICT);
        }
        return new OrderPaymentResponse(orderNo, OrderStatus.PAID, paidAt, externalTradeNo, OrderPaymentType.SIMULATED, 0L, null);
    }

    private String externalTradeNo(String rawExternalTradeNo, String orderNo) {
        String value = rawExternalTradeNo == null ? "" : rawExternalTradeNo.trim();
        if (!value.isEmpty()) {
            return value;
        }
        return "MOCKPAY-" + orderNo + "-" + UUID.randomUUID();
    }
}
