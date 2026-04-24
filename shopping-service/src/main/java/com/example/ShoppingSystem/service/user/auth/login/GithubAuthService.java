package com.example.ShoppingSystem.service.user.auth.login;

import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;

/**
 * GitHub 登录身份服务接口。
 */
public interface GithubAuthService {

    UserLoginIdentity loginByGithub(String githubId, String githubEmail);
}
