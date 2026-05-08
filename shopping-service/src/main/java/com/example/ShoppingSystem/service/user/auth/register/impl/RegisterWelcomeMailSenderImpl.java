package com.example.ShoppingSystem.service.user.auth.register.impl;

import com.example.ShoppingSystem.service.mail.ShoppingMailSender;
import com.example.ShoppingSystem.service.user.auth.register.RegisterWelcomeMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RegisterWelcomeMailSenderImpl implements RegisterWelcomeMailSender {

    private final ShoppingMailSender shoppingMailSender;

    public RegisterWelcomeMailSenderImpl(ShoppingMailSender shoppingMailSender) {
        this.shoppingMailSender = shoppingMailSender;
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendWelcomeMail(String email) {
        shoppingMailSender.sendText(
                email,
                "欢迎使用 Shopping System",
                "欢迎注册 Shopping System，你的账号已经创建成功。"
        );
    }
}
