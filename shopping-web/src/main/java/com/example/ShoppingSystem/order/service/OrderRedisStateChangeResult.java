package com.example.ShoppingSystem.order.service;

import java.util.List;
import java.util.Map;

public record OrderRedisStateChangeResult(boolean changed,
                                          String code,
                                          Map<String, Object> order,
                                          List<Map<String, Object>> items) {

    public static OrderRedisStateChangeResult unchanged(String code) {
        return new OrderRedisStateChangeResult(false, code, Map.of(), List.of());
    }

    public static OrderRedisStateChangeResult changed(Map<String, Object> order,
                                                      List<Map<String, Object>> items) {
        return changed("OK", order, items);
    }

    public static OrderRedisStateChangeResult changed(String code,
                                                      Map<String, Object> order,
                                                      List<Map<String, Object>> items) {
        return new OrderRedisStateChangeResult(true, code, order, items);
    }
}
