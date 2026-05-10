package com.example.ShoppingSystem.admin.dto;

public record AdminFirstLoginCompleteRequest(String username,
                                             String email,
                                             String phone,
                                             String password,
                                             String emailCode) {
}
