package com.example.ShoppingSystem.service.user.auth.register.impl;

import com.example.ShoppingSystem.service.user.auth.register.RegisterEmailCodeMailSender;
import com.example.ShoppingSystem.service.mail.ShoppingMailSender;
import org.springframework.stereotype.Service;

/**
 * Synchronous mail sender used by the RabbitMQ consumer so send failures can be retried.
 */
@Service
public class RegisterEmailCodeMailSenderImpl implements RegisterEmailCodeMailSender {

    private final ShoppingMailSender shoppingMailSender;

    public RegisterEmailCodeMailSenderImpl(ShoppingMailSender shoppingMailSender) {
        this.shoppingMailSender = shoppingMailSender;
    }

    @Override
    public void sendRegisterEmailCode(String email, String code, long expireMinutes) {
        shoppingMailSender.sendText(
                email,
                "注册验证码",
                String.format(
                        "你的注册验证码是 %s，%d 分钟内有效。请勿将验证码告诉他人。",
                        code,
                        expireMinutes
                )
        );
    }
}
