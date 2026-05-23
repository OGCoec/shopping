package com.example.ShoppingSystem.admin.dto;

public record AdminProductSpuBatchDisableResponse(int requestedCount,
                                                  int matchedCount,
                                                  int affectedCount) {
}
