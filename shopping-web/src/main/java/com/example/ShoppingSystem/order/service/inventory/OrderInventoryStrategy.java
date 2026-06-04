package com.example.ShoppingSystem.order.service.inventory;

import com.example.ShoppingSystem.order.service.OrderCreateContext;
import com.example.ShoppingSystem.order.service.OrderInventoryItem;

import java.util.List;

public interface OrderInventoryStrategy {

    OrderInventoryType type();

    OrderInventoryDeductResult deduct(OrderCreateContext context);

    void release(OrderInventoryItem item);

    void releaseAll(List<OrderInventoryItem> items);
}
