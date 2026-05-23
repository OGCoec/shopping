package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminRiskApiProviderConfigResponse(String provider,
                                                 String displayName,
                                                 String propertyPrefix,
                                                 List<AdminRiskApiConfigField> fields,
                                                 String windowsEnvTarget,
                                                 String envTarget,
                                                 String envStoreType,
                                                 boolean restartRequired) {
}
