package com.example.ShoppingSystem.order.dto;

import java.util.List;

public record OrderCardSecretResponse(String orderNo,
                                      String orderStatus,
                                      String deliveryStatus,
                                      Integer requiredCount,
                                      Integer deliveredCount,
                                      List<OrderCardSecretItemResponse> items) {
}
