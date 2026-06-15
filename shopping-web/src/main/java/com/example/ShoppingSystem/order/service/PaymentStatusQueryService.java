package com.example.ShoppingSystem.order.service;

public interface PaymentStatusQueryService {

    PaymentStatusQueryResult query(String orderNo, Long userId);
}
