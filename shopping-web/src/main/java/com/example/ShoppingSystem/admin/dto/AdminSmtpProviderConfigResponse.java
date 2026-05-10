package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminSmtpProviderConfigResponse(String provider,
                                              String displayName,
                                              String description,
                                              boolean current,
                                              String currentProvider,
                                              String currentProviderDisplayName,
                                              String windowsEnvTarget,
                                              boolean restartRequired,
                                              List<AdminSmtpConfigField> fields) {
}
