package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.config.datasource.OrderReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.dto.OrderDetailResponse;
import com.example.ShoppingSystem.order.dto.OrderItemResponse;
import com.example.ShoppingSystem.order.dto.OrderPageItemResponse;
import com.example.ShoppingSystem.order.dto.OrderPageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface OrderQueryService {
    public OrderDetailResponse detail(Long userId, String orderNo);

    public OrderPageResponse page(Long userId, Integer rawPage, Integer rawPageSize, String status);
}
