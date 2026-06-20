package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.mapper.order.OrderCardSecretDeliveryMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public interface OrderCardSecretDeliveryService {
    public record DeliveryBatchResult(int requiredCount,
                                          int deliveredCount,
                                          int shortageCount,
                                          boolean lockBusy) {
            public static DeliveryBatchResult empty() {
                return new DeliveryBatchResult(0, 0, 0, false);
            }
        }

    public DeliveryBatchResult deliverPaidOrder(String orderNo,
                                                Long userId,
                                                List<Map<String, Object>> items);

    public DeliveryBatchResult deliverPaidOrdersFromRows(List<Map<String, Object>> paidRows);
}
