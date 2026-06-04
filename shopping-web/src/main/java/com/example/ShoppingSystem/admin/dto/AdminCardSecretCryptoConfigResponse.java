package com.example.ShoppingSystem.admin.dto;

public record AdminCardSecretCryptoConfigResponse(String activeKeyVersion,
                                                  String activeKeyVersionEnvName,
                                                  AdminCardSecretCryptoConfigField aesKey,
                                                  AdminCardSecretCryptoConfigField hmacKey,
                                                  String windowsEnvTarget,
                                                  String envTarget,
                                                  String envStoreType,
                                                  boolean restartRequired) {
}
