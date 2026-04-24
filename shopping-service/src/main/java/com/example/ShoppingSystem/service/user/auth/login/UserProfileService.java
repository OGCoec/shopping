package com.example.ShoppingSystem.service.user.auth.login;

import com.example.ShoppingSystem.service.user.auth.login.model.UserProfileDraft;

/**
 * 用户资料服务接口。
 */
public interface UserProfileService {

    void initIfAbsent(Long userId, UserProfileDraft draft);
}
