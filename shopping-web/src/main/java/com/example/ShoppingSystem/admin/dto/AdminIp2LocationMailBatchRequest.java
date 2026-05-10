package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminIp2LocationMailBatchRequest(List<String> credentialLines,
                                                Integer threadPoolSize) {
}
