package com.example.ShoppingSystem.order.dto;

import java.util.List;

public record OrderCardSecretItemResponse(String skuId,
                                          String skuName,
                                          Integer quantity,
                                          Integer deliveredCount,
                                          List<OrderCardSecretValueResponse> secrets) {
}
