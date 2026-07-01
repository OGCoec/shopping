package com.example.ShoppingSystem.order.service;
import java.util.List;
import java.util.Map;
public interface OrderInventoryReleaseService {
    public void release(String orderNo, List<Map<String, Object>> itemRows);

    public int releaseAll(List<OrderRedisSnapshot> snapshots);
}
