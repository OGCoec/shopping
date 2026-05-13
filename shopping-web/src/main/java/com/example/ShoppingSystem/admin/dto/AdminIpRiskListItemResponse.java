package com.example.ShoppingSystem.admin.dto;

public record AdminIpRiskListItemResponse(String ip,
                                          int score,
                                          String level,
                                          String countryCode,
                                          String countryName,
                                          String dialCode,
                                          String flagCode,
                                          String region,
                                          String city,
                                          String asn,
                                          String providerName,
                                          String ipType,
                                          boolean datacenter,
                                          boolean vpn,
                                          boolean proxy,
                                          boolean tor,
                                          String sourceProvider,
                                          String lastSeenAt,
                                          String queriedAt,
                                          String expiresAt) {
}
