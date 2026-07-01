package com.example.ShoppingSystem.order.service;
import com.example.ShoppingSystem.order.dto.OrderCreateRequest;
import com.example.ShoppingSystem.order.dto.OrderCreateResponse;
public interface OrderCreateService {
    public OrderCreateResponse create(Long userId, OrderCreateRequest request);
}
