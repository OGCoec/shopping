package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminDeviceRiskListResponse(String level,
                                          int page,
                                          int pageSize,
                                          long total,
                                          boolean hasNext,
                                          String sort,
                                          String source,
                                          List<AdminDeviceRiskListItemResponse> items) {
}
