package com.example.ShoppingSystem.admin.dto;

public record AdminSmtpConfigField(String label,
                                   String maskedValue,
                                   String propertyKey,
                                   String envName,
                                   String windowsEnvTarget,
                                   String envTarget,
                                   String envStoreType,
                                   String yamlFile,
                                   Integer yamlLine,
                                   boolean sensitive) {
}
