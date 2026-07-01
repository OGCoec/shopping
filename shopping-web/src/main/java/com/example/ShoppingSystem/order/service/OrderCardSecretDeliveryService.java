package com.example.ShoppingSystem.order.service;
import java.util.List;
import java.util.Map;
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
