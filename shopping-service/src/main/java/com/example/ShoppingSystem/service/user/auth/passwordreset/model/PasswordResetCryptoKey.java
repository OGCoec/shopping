package com.example.ShoppingSystem.service.user.auth.passwordreset.model;

import java.util.Map;

public record PasswordResetCryptoKey(String kid,
                                     String alg,
                                     Map<String, Object> publicKeyJwk,
                                     long expiresAtEpochMillis) {
}
