package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminIp2LocationBinLookupResponse(String pattern,
                                                long candidateCount,
                                                long queriedCount,
                                                long matchedCount,
                                                long unmatchedCount,
                                                int page,
                                                int pageSize,
                                                long total,
                                                boolean hasNext,
                                                List<AdminIp2LocationBinLookupItemResponse> items) {
}
