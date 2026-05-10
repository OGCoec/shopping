package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.service.mail.ShoppingMailSender;
import org.springframework.stereotype.Service;

@Service
public class AdminMailService {

    private final ShoppingMailSender shoppingMailSender;

    public AdminMailService(ShoppingMailSender shoppingMailSender) {
        this.shoppingMailSender = shoppingMailSender;
    }

    public void sendFirstLoginEmailCode(String email, String code, long expireMinutes) {
        shoppingMailSender.sendText(
                email,
                "管理员初始化验证码",
                "你的管理员初始化验证码是 " + code + "，" + expireMinutes
                        + " 分钟内有效。\n如果不是你本人操作，请忽略本邮件。"
        );
    }
}
