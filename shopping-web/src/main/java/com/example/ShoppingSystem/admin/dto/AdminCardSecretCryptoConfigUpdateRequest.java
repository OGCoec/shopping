package com.example.ShoppingSystem.admin.dto;

public record AdminCardSecretCryptoConfigUpdateRequest(String activeKeyVersion,
                                                       String aesKeyBase64,
                                                       String hmacKeyBase64) {
}
