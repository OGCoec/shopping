package com.example.ShoppingSystem.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(String skuId,
                                Long spuId,
                                String skuCode,
                                String skuName,
                                String specJson,
                                String skuImageUrl,
                                Integer quantity,
                                BigDecimal salePriceYuan,
                                BigDecimal lineAmountYuan,
                                boolean hotSku) {
}
