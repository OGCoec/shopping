package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminProductSkuBatchStatusRequest(List<String> ids,
                                                String status) {
}
