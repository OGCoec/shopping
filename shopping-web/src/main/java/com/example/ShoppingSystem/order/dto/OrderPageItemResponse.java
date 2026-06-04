package com.example.ShoppingSystem.order.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderPageItemResponse(String orderNo,
                                    String status,
                                    BigDecimal payAmountYuan,
                                    OffsetDateTime expireAt,
                                    OffsetDateTime createdAt,
                                    String firstSkuName,
                                    String firstSkuImageUrl,
                                    Integer itemCount) {
}
