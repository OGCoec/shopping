package com.example.ShoppingSystem.service.user.auth.register;

/**
 * Sends registration verification emails synchronously from the MQ consumer.
 */
public interface RegisterEmailCodeMailSender {

    void sendRegisterEmailCode(String email, String code, long expireMinutes);
}
