package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.dto.OrderPreviewRequest;
import com.example.ShoppingSystem.order.dto.OrderPreviewResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface OrderPreviewService {
    public OrderPreviewResponse preview(Long userId, OrderPreviewRequest request);
}
