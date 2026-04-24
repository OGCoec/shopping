package com.example.ShoppingSystem.entity.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginIdentity {

    private Long id;
    private Long userId;

    private String email;
    private String emailPasswordHash;
    private Boolean emailVerified;

    private String phone;
    private Boolean phoneVerified;

    private String githubId;
    private String googleId;
    private String microsoftId;

    private String tokenVersion;
    private String status;

    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
