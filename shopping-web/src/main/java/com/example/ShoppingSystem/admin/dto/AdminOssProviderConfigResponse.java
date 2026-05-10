package com.example.ShoppingSystem.admin.dto;

public record AdminOssProviderConfigResponse(String provider,
                                             AdminOAuth2ConfigField accessKeyId,
                                             AdminOAuth2ConfigField accessKeySecret,
                                             String windowsEnvTarget,
                                             boolean restartRequired,
                                             boolean adminRequired) {
}
