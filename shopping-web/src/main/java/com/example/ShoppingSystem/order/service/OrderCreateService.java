package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.dto.OrderCreateRequest;
import com.example.ShoppingSystem.order.dto.OrderCreateResponse;
import com.example.ShoppingSystem.order.rabbit.OrderExpireMessagePublisher;
import com.example.ShoppingSystem.order.rabbit.OrderExpireRabbitProperties;
import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryDeductResult;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryStrategy;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface OrderCreateService {
    public OrderCreateResponse create(Long userId, OrderCreateRequest request);
}
