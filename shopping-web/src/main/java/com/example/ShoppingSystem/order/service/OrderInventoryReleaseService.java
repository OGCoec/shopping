package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.service.inventory.OrderInventoryStrategy;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderInventoryReleaseService {

    private final Map<OrderInventoryType, OrderInventoryStrategy> inventoryStrategies;

    public OrderInventoryReleaseService(List<OrderInventoryStrategy> strategies) {
        this.inventoryStrategies = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(OrderInventoryStrategy::type, Function.identity()));
    }

    public void release(String orderNo, List<Map<String, Object>> itemRows) {
        if (itemRows == null || itemRows.isEmpty()) {
            return;
        }
        Map<OrderInventoryType, List<OrderInventoryItem>> groupedItems = itemRows.stream()
                .map(row -> toInventoryItem(orderNo, row))
                .filter(item -> item.quantity() > 0)
                .collect(Collectors.groupingBy(this::type));
        groupedItems.forEach((type, items) -> {
            OrderInventoryStrategy strategy = inventoryStrategies.get(type);
            if (strategy == null) {
                throw new OrderServiceException("ORDER_INVENTORY_STRATEGY_NOT_FOUND", "Order inventory strategy is missing.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            strategy.releaseAll(items);
        });
    }

    private OrderInventoryItem toInventoryItem(String orderNo, Map<String, Object> row) {
        return new OrderInventoryItem(
                orderNo,
                OrderRowMapper.longValue(row, "userId"),
                OrderRowMapper.idBytes(row, "skuId"),
                OrderRowMapper.idText(row, "skuId"),
                OrderRowMapper.intValue(row, "quantity", 0),
                OrderRowMapper.boolValue(row, "hotSku")
        );
    }

    private OrderInventoryType type(OrderInventoryItem item) {
        return item.hotSku() ? OrderInventoryType.HOT : OrderInventoryType.NORMAL;
    }
}
