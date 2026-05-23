package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminProductSpuPageResponse(long total,
                                          int page,
                                          int pageSize,
                                          List<AdminProductSpuResponse> records) {
}
