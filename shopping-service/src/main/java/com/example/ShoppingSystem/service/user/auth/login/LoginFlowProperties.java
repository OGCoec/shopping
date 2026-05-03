package com.example.ShoppingSystem.service.user.auth.login;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "login.flow")
public class LoginFlowProperties {

    private String redisKeyPrefix = "auth:login:flow:";
    private int ttlMinutes = 15;
    private String cookieName = "LOGIN_FLOW_ID";
    private String cookiePath = "/shopping/user";
    private boolean cookieHttpOnly = true;
    private boolean cookieSecure = false;
    private String cookieSameSite = "Lax";
}
