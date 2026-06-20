package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.admin.dto.AdminOrderDtos.AdminOrderDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminOrderDtos.AdminOrderItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminOrderDtos.AdminOrderListItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminOrderDtos.AdminOrderPageResponse;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.config.datasource.OrderReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import org.springframework.http.HttpStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface AdminOrderQueryService {
    public AdminOrderPageResponse page(Integer rawPage, Integer rawPageSize, String rawStatus, String rawOrderNo);

    public AdminOrderDetailResponse detail(String rawOrderNo);
}
