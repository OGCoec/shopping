package com.example.ShoppingSystem.entity.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 用户资料实体。
 * 与 user_profile 表字段一一对应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    private Long id;
    private String firstName;
    private String lastName;

    private String gender;
    private String bio;
    private LocalDate birthday;

    private String country;
    private String language;
    private String timezone;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
