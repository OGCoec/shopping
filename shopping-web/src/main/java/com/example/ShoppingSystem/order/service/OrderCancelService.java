package com.example.ShoppingSystem.order.service;
import com.example.ShoppingSystem.order.dto.OrderCancelResponse;
public interface OrderCancelService {
    public OrderCancelResponse cancel(Long userId, String orderNo);
}
