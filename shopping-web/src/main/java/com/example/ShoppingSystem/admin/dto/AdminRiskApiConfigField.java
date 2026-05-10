package com.example.ShoppingSystem.admin.dto;

public record AdminRiskApiConfigField(String id,
                                      String label,
                                      String maskedValue,
                                      String propertyKey,
                                      String envName,
                                      String windowsEnvTarget,
                                      String yamlFile,
                                      Integer yamlLine,
                                      boolean sensitive) {
}
