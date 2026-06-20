package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.dto.OrderCancelResponse;
import org.springframework.http.HttpStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface OrderCancelService {
    public OrderCancelResponse cancel(Long userId, String orderNo);
}
