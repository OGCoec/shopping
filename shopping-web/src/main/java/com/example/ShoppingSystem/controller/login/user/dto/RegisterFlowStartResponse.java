package com.example.ShoppingSystem.controller.login.user.dto;

public record RegisterFlowStartResponse(boolean success,
                                        String message,
                                        String nextPath) {
}
