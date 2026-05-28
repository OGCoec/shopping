package com.example.ShoppingSystem.product.dto;

import java.util.List;

public record PublicProductSearchResponse(long total,
                                          int page,
                                          int pageSize,
                                          List<PublicProductSummaryResponse> records) {
}
