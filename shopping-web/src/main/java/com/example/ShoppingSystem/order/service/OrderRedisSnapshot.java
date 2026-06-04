package com.example.ShoppingSystem.order.service;

import java.util.List;
import java.util.Map;

public record OrderRedisSnapshot(Map<String, Object> order,
                                 List<Map<String, Object>> items) {
}
