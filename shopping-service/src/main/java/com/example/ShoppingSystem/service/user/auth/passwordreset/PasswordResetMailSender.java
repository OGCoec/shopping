package com.example.ShoppingSystem.service.user.auth.passwordreset;

public interface PasswordResetMailSender {

    void sendResetCode(String email, String code, long expireMinutes);

    void sendResetLink(String email, String resetUrl, long expireMinutes);

    void sendResetCodeAndLink(String email,
                              String code,
                              String resetUrl,
                              long codeExpireMinutes,
                              long linkExpireMinutes);
}
