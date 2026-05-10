package com.example.ShoppingSystem.admin.dto;

public record AdminSmtpProviderSummary(String provider,
                                       String displayName,
                                       String description,
                                       boolean current) {
}
