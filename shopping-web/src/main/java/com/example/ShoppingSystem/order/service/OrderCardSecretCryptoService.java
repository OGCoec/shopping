package com.example.ShoppingSystem.order.service;

public interface OrderCardSecretCryptoService {
    public String decrypt(String ciphertextBase64, String nonceBase64, String keyVersion);
}
