package com.example.ShoppingSystem.controller;

public record GlobalErrorResponse(
        boolean success,
        int status,
        String error,
        String message,
        String path,
        String timestamp
) {
}
