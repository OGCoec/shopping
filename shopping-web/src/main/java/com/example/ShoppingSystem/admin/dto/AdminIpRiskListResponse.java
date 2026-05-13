package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminIpRiskListResponse(String family,
                                      AdminIpRiskCountryResponse country,
                                      String level,
                                      int page,
                                      int pageSize,
                                      long total,
                                      boolean hasNext,
                                      String sort,
                                      String source,
                                      List<AdminIpRiskListItemResponse> items) {
}
