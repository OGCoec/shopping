package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.dto.OrderPaymentRequest;
import com.example.ShoppingSystem.order.dto.OrderPaymentResponse;

public interface OrderPaymentProcessor {

    String paymentType();

    OrderPaymentResponse pay(Long userId, String orderNo, OrderPaymentRequest request);
}
