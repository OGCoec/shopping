package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.dto.OrderPreviewRequest;
import com.example.ShoppingSystem.order.dto.OrderPreviewResponse;
public interface OrderPreviewService {
    public OrderPreviewResponse preview(Long userId, OrderPreviewRequest request);
}
