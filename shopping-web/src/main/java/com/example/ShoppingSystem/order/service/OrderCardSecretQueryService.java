package com.example.ShoppingSystem.order.service;
import com.example.ShoppingSystem.order.dto.OrderCardSecretResponse;
public interface OrderCardSecretQueryService {
    public static final String DELIVERY_STATUS_DELIVERED = "DELIVERED";

    public static final String DELIVERY_STATUS_PENDING = "PENDING";

    public OrderCardSecretResponse getForUser(Long userId, String orderNo);
}
