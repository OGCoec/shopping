package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.service.inventory.OrderInventoryStrategy;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryType;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface OrderInventoryReleaseService {
    public void release(String orderNo, List<Map<String, Object>> itemRows);

    public int releaseAll(List<OrderRedisSnapshot> snapshots);
}
