package com.example.ShoppingSystem.service.user.auth.login;

import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;

/**
 * Google 登录身份服务接口。
 */
public interface GoogleAuthService {

    UserLoginIdentity loginByGoogle(String googleId, String googleEmail);
}
