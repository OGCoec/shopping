package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminSmtpProvidersResponse(List<AdminSmtpProviderSummary> providers,
                                         String currentProvider,
                                         String currentProviderDisplayName) {
}
