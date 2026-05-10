package com.example.ShoppingSystem.admin.dto;

public record AdminSmsProviderConfigResponse(String provider,
                                             String displayName,
                                             String description,
                                             AdminOAuth2ConfigField accessKeyId,
                                             AdminOAuth2ConfigField accessKeySecret,
                                             String windowsEnvTarget,
                                             boolean restartRequired) {
}
