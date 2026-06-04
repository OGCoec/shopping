package com.example.ShoppingSystem.admin.dto;

public record AdminCardSecretCryptoConfigField(String envName,
                                               String maskedValue,
                                               boolean configured,
                                               Integer requiredDecodedBytes,
                                               Integer minDecodedBytes) {
}
