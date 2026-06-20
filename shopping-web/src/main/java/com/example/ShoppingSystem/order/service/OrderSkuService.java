package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

public interface OrderSkuService {
    public OrderSkuSnapshot loadActiveSku(String rawSkuId, OffsetDateTime now);
}
