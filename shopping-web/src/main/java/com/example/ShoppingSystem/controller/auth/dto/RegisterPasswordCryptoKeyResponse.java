package com.example.ShoppingSystem.controller.auth.dto;

import java.util.Map;

/**
 * 临时注册密码加密公钥响应。
 */
public record RegisterPasswordCryptoKeyResponse(String kid,
                                                String alg,
                                                Map<String, Object> publicKeyJwk,
                                                long expiresAtEpochMillis) {
}
