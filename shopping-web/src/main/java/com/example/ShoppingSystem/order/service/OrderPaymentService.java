package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.dto.OrderPaymentRequest;
import com.example.ShoppingSystem.order.dto.OrderPaymentResponse;
public interface OrderPaymentService {
    public OrderPaymentResponse pay(Long userId, String rawOrderNo, OrderPaymentRequest request);
}
