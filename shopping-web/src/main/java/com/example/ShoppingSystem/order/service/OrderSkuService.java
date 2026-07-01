package com.example.ShoppingSystem.order.service;
import java.time.OffsetDateTime;
public interface OrderSkuService {
    public OrderSkuSnapshot loadActiveSku(String rawSkuId, OffsetDateTime now);
}
