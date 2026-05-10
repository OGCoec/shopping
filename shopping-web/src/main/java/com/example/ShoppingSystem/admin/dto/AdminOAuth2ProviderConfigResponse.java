package com.example.ShoppingSystem.admin.dto;

public record AdminOAuth2ProviderConfigResponse(String provider,
                                                AdminOAuth2ConfigField clientId,
                                                AdminOAuth2ConfigField clientSecret,
                                                String windowsEnvTarget,
                                                boolean restartRequired) {
}
