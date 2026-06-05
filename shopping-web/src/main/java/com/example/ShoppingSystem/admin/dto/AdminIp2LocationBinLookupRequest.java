package com.example.ShoppingSystem.admin.dto;

public record AdminIp2LocationBinLookupRequest(String ipPattern,
                                               String countryCode,
                                               String region,
                                               String city,
                                               Boolean includeUnmatched,
                                               Integer page,
                                               Integer pageSize) {
}
