package com.example.ShoppingSystem.registerflow;

public record RegisterFlowErrorResponse(
        boolean success,
        int status,
        String error,
        String message,
        String path,
        String timestamp,
        String redirectPath,
        String registerNotice
) {
}
