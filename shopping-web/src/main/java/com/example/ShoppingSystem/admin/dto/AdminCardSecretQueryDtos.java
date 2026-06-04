package com.example.ShoppingSystem.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class AdminCardSecretQueryDtos {

    private AdminCardSecretQueryDtos() {
    }

    public record AdminCardSecretInventoryPageResponse(int page,
                                                       int pageSize,
                                                       long total,
                                                       int pages,
                                                       List<AdminCardSecretInventoryItemResponse> records) {
    }

    public record AdminCardSecretInventoryItemResponse(String cardSecretId,
                                                       String skuId,
                                                       String skuName,
                                                       Long spuId,
                                                       String spuName,
                                                       String batchNo,
                                                       String importSource,
                                                       String inventoryStatus,
                                                       String deliveryStatus,
                                                       String orderNo,
                                                       String orderStatus,
                                                       Long userId,
                                                       OffsetDateTime soldAt,
                                                       OffsetDateTime deliveredAt,
                                                       OffsetDateTime createdAt,
                                                       String createdByAdminUsername,
                                                       String createdByAdminEmail) {
    }

    public record AdminCardSecretDeliveryPageResponse(int page,
                                                      int pageSize,
                                                      long total,
                                                      int pages,
                                                      List<AdminCardSecretDeliveryItemResponse> records) {
    }

    public record AdminCardSecretDeliveryItemResponse(String deliveryId,
                                                      String cardSecretId,
                                                      String skuId,
                                                      String skuName,
                                                      Long spuId,
                                                      String spuName,
                                                      String orderNo,
                                                      String orderStatus,
                                                      Long userId,
                                                      String inventoryStatus,
                                                      String deliveryStatus,
                                                      OffsetDateTime deliveredAt,
                                                      OffsetDateTime revokedAt,
                                                      OffsetDateTime refundedAt,
                                                      OffsetDateTime replacedAt,
                                                      String createdByAdminUsername) {
    }

    public record AdminCardSecretRevealResponse(String cardSecretId,
                                                String skuId,
                                                String skuName,
                                                String inventoryStatus,
                                                String deliveryStatus,
                                                String orderNo,
                                                String orderStatus,
                                                Long userId,
                                                String secret) {
    }
}
