package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.admin.dto.AdminOrderDtos.AdminOrderDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminOrderDtos.AdminOrderPageResponse;
public interface AdminOrderQueryService {
    public AdminOrderPageResponse page(Integer rawPage, Integer rawPageSize, String rawStatus, String rawOrderNo);

    public AdminOrderDetailResponse detail(String rawOrderNo);
}
