package com.example.ShoppingSystem.admin.dto;

public record AdminCaptchaConfigField(String maskedValue,
                                      String propertyKey,
                                      String envName,
                                      String windowsEnvTarget,
                                      String yamlFile,
                                      Integer yamlLine) {
}
