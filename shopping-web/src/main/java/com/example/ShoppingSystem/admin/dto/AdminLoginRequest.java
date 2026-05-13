package com.example.ShoppingSystem.admin.dto;

public record AdminLoginRequest(String identifier,
                                String password,
                                String passwordCipher,
                                String kid,
                                String nonce,
                                Long timestamp) {
}
