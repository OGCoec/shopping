package com.example.ShoppingSystem.admin.dto;

public record AdminOAuth2ConfigField(String maskedValue,
                                     String propertyKey,
                                     String envName,
                                     String windowsEnvTarget,
                                     String envTarget,
                                     String envStoreType,
                                     String yamlFile,
                                     Integer yamlLine) {
}
