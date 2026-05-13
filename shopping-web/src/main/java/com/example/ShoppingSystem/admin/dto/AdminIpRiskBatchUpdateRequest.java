package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminIpRiskBatchUpdateRequest(List<String> ips,
                                            int targetScore,
                                            String action) {
}
