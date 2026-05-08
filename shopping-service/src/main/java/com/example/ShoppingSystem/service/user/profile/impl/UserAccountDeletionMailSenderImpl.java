package com.example.ShoppingSystem.service.user.profile.impl;

import com.example.ShoppingSystem.service.user.profile.UserAccountDeletionMailSender;
import com.example.ShoppingSystem.service.mail.ShoppingMailSender;
import org.springframework.stereotype.Service;

@Service
public class UserAccountDeletionMailSenderImpl implements UserAccountDeletionMailSender {

    private final ShoppingMailSender shoppingMailSender;

    public UserAccountDeletionMailSenderImpl(ShoppingMailSender shoppingMailSender) {
        this.shoppingMailSender = shoppingMailSender;
    }

    @Override
    public void sendDeletionQueued(String email) {
        shoppingMailSender.sendText(
                email,
                "账号注销请求已提交",
                "你的账号已进入注销处理期。系统将在 7 天后完成账号注销。在此期间账号将不可登录。"
        );
    }

    @Override
    public void sendDeletionCompleted(String email) {
        shoppingMailSender.sendText(
                email,
                "账号注销成功",
                "你的账号已注销成功，相关登录身份和用户资料已完成删除。"
        );
    }
}
