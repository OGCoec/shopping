package com.example.ShoppingSystem.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminOrderDtos {

    private AdminOrderDtos() {
    }

    public record AdminOrderPageResponse(int page,
                                         int pageSize,
                                         long total,
                                         List<AdminOrderListItemResponse> records) {
    }

    public record AdminOrderListItemResponse(String orderNo,
                                             Long userId,
                                             String status,
                                             BigDecimal totalAmountYuan,
                                             BigDecimal discountAmountYuan,
                                             BigDecimal payAmountYuan,
                                             String userCouponId,
                                             OffsetDateTime expireAt,
                                             OffsetDateTime paidAt,
                                             OffsetDateTime closingAt,
                                             OffsetDateTime closingDeadlineAt,
                                             OffsetDateTime cancelledAt,
                                             OffsetDateTime closedAt,
                                             OffsetDateTime createdAt,
                                             OffsetDateTime updatedAt,
                                             String firstSkuName,
                                             String firstSkuImageUrl,
                                             Integer itemCount,
                                             String storageSource) {
    }

    public record AdminOrderDetailResponse(String orderNo,
                                           Long userId,
                                           String status,
                                           BigDecimal totalAmountYuan,
                                           BigDecimal discountAmountYuan,
                                           BigDecimal payAmountYuan,
                                           Long requiredPoints,
                                           String userCouponId,
                                           OffsetDateTime expireAt,
                                           OffsetDateTime paidAt,
                                           OffsetDateTime closingAt,
                                           OffsetDateTime closingDeadlineAt,
                                           OffsetDateTime cancelledAt,
                                           OffsetDateTime closedAt,
                                           OffsetDateTime createdAt,
                                           OffsetDateTime updatedAt,
                                           String storageSource,
                                           List<AdminOrderItemResponse> items) {
    }

    public record AdminOrderItemResponse(String skuId,
                                         Long spuId,
                                         String skuCode,
                                         String skuName,
                                         String specJson,
                                         String skuImageUrl,
                                         Integer quantity,
                                         BigDecimal salePriceYuan,
                                         BigDecimal lineAmountYuan,
                                         boolean hotSku,
                                         OffsetDateTime createdAt) {
    }
}
