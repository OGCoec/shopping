package com.example.ShoppingSystem.admin.dto;

public record AdminCaptchaProviderConfigResponse(String provider,
                                                 AdminCaptchaConfigField siteKey,
                                                 AdminCaptchaConfigField secretKey,
                                                 String windowsEnvTarget,
                                                 String envTarget,
                                                 String envStoreType,
                                                 boolean restartRequired) {
}
