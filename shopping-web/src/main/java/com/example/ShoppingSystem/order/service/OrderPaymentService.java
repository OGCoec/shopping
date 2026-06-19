package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.dto.OrderPaymentRequest;
import com.example.ShoppingSystem.order.dto.OrderPaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderPaymentService {

    private final Map<String, OrderPaymentProcessor> processors;

    public OrderPaymentService(List<OrderPaymentProcessor> processors) {
        this.processors = processors.stream()
                .collect(Collectors.toUnmodifiableMap(
                        processor -> normalizePaymentType(processor.paymentType()),
                        Function.identity()
                ));
    }

    public OrderPaymentResponse pay(Long userId, String rawOrderNo, OrderPaymentRequest request) {
        String orderNo = normalizeOrderNo(rawOrderNo);
        String paymentType = paymentType(request);
        OrderPaymentProcessor processor = processors.get(paymentType);
        if (processor == null) {
            throw new OrderServiceException("ORDER_PAYMENT_TYPE_UNSUPPORTED", "Payment type is unsupported.", HttpStatus.BAD_REQUEST);
        }
        return processor.pay(userId, orderNo, request);
    }

    private String paymentType(OrderPaymentRequest request) {
        String rawPaymentType = request == null ? null : request.paymentType();
        String normalized = normalizePaymentType(rawPaymentType);
        return normalized.isBlank() ? OrderPaymentType.SIMULATED : normalized;
    }

    private String normalizePaymentType(String paymentType) {
        return paymentType == null ? "" : paymentType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOrderNo(String orderNo) {
        String value = orderNo == null ? "" : orderNo.trim();
        if (value.isEmpty() || value.length() > 64) {
            throw new OrderServiceException("ORDER_NO_INVALID", "Order number is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }
}
