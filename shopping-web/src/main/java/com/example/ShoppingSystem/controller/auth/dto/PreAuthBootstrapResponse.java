package com.example.ShoppingSystem.controller.auth.dto;

/**
 * 预登录 bootstrap 响应。
 */
public record PreAuthBootstrapResponse(boolean success,
                                       String message,
                                       String token,
                                       long expiresAtEpochMillis,
                                       String riskLevel,
                                       boolean challengeRequired,
                                       boolean blocked) {
}
