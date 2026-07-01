package com.example.ShoppingSystem.order.service;
import com.example.ShoppingSystem.order.dto.OrderDetailResponse;
import com.example.ShoppingSystem.order.dto.OrderPageResponse;
public interface OrderQueryService {
    public OrderDetailResponse detail(Long userId, String orderNo);

    public OrderPageResponse page(Long userId, Integer rawPage, Integer rawPageSize, String status);
}
