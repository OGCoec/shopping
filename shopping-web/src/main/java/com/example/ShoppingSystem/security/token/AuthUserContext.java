package com.example.ShoppingSystem.security.token;

import java.util.Set;

public record AuthUserContext(Long userId,
                              String username,
                              String firstName,
                              String lastName,
                              String account,
                              String email,
                              String phone,
                              String status,
                              String gender,
                              String avatarUrl,
                              String tokenVersion,
                              String riskLevel,
                              Set<String> roles) {
}
