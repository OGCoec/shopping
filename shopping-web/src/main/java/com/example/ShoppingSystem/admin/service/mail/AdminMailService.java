package com.example.ShoppingSystem.admin.service.mail;

import com.example.ShoppingSystem.service.mail.ShoppingMailSender;

public interface AdminMailService {
    public void sendFirstLoginEmailCode(String email, String code, long expireMinutes);
}
