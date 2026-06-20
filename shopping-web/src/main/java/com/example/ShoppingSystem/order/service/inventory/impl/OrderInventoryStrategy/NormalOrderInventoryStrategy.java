package com.example.ShoppingSystem.order.service.inventory.impl.OrderInventoryStrategy;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.mapper.product.OrderProductSkuMapper;
import com.example.ShoppingSystem.order.service.OrderCreateContext;
import com.example.ShoppingSystem.order.service.OrderInventoryItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.ShoppingSystem.order.service.inventory.OrderInventoryDeductResult;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryStrategy;
import com.example.ShoppingSystem.order.service.inventory.OrderInventoryType;
@Component
public class NormalOrderInventoryStrategy implements OrderInventoryStrategy {

    private final OrderProductSkuMapper orderProductSkuMapper;
    private final ObjectMapper objectMapper;

    public NormalOrderInventoryStrategy(OrderProductSkuMapper orderProductSkuMapper,
                                        ObjectMapper objectMapper) {
        this.orderProductSkuMapper = orderProductSkuMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public OrderInventoryType type() {
        return OrderInventoryType.NORMAL;
    }

    @Override
    public OrderInventoryDeductResult deduct(OrderCreateContext context) {
        int affected = orderProductSkuMapper.deductNormalSkuStock(context.sku().skuId(), context.quantity());
        if (affected != 1) {
            return OrderInventoryDeductResult.fail("ORDER_STOCK_NOT_ENOUGH", "SKU stock is not enough.");
        }
        return OrderInventoryDeductResult.success(null);
    }

    @Override
    public void release(OrderInventoryItem item) {
        orderProductSkuMapper.increaseNormalSkuStock(item.skuId(), item.quantity());
    }

    @Override
    public void releaseAll(List<OrderInventoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Map<String, Object>> payload = items.stream()
                .filter(item -> item.quantity() > 0)
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sku_id_hex", HybridIdCodec.toHex(item.skuId()));
                    row.put("quantity", item.quantity());
                    return row;
                })
                .toList();
        if (payload.isEmpty()) {
            return;
        }
        try {
            orderProductSkuMapper.increaseNormalSkuStocks(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Normal SKU inventory release payload is invalid.", e);
        }
    }
}
