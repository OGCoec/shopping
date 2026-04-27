package com.example.ShoppingSystem.service.user.auth.login.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * OAuth 首次登录时的用户资料草稿。
 * 仅承载可从第三方 provider 获取到的初始化字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDraft {

    private String username;
    private String firstName;
    private String lastName;

    private String gender;
    private String bio;
    private LocalDate birthday;

    private String country;
    private String language;
    private String timezone;
}
