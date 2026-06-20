package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.config.datasource.OrderReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.order.OrderCardSecretDeliveryMapper;
import com.example.ShoppingSystem.order.dto.OrderCardSecretItemResponse;
import com.example.ShoppingSystem.order.dto.OrderCardSecretResponse;
import com.example.ShoppingSystem.order.dto.OrderCardSecretValueResponse;
import org.springframework.http.HttpStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface OrderCardSecretQueryService {
    public static final String DELIVERY_STATUS_DELIVERED = "DELIVERED";

    public static final String DELIVERY_STATUS_PENDING = "PENDING";

    public OrderCardSecretResponse getForUser(Long userId, String orderNo);
}
