package com.example.ShoppingSystem.order.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderPreviewResponse(String skuId,
                                   Long spuId,
                                   String skuName,
                                   String skuImageUrl,
                                   Integer quantity,
                                   BigDecimal salePriceYuan,
                                   BigDecimal lineAmountYuan,
                                   boolean hotSku,
                                   String selectedUserCouponId,
                                   BigDecimal totalAmountYuan,
                                   BigDecimal discountAmountYuan,
                                   BigDecimal payAmountYuan,
                                   List<OrderCouponOptionResponse> availableCoupons,
                                   List<OrderCouponOptionResponse> unavailableCoupons) {
}
