package com.example.ShoppingSystem.admin.service.mail;

public interface AdminMailService {
    public void sendFirstLoginEmailCode(String email, String code, long expireMinutes);
}
