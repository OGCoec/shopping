package com.example.ShoppingSystem.admin.dto;

public record AdminSessionMeResponse(boolean authenticated,
                                     String username,
                                     String email,
                                     String phone) {
}
