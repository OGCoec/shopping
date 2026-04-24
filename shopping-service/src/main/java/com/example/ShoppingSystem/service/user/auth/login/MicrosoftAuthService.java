package com.example.ShoppingSystem.service.user.auth.login;

import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;

/**
 * Microsoft 登录身份服务接口。
 */
public interface MicrosoftAuthService {

    UserLoginIdentity loginByMicrosoft(String microsoftId, String microsoftEmail);
}
