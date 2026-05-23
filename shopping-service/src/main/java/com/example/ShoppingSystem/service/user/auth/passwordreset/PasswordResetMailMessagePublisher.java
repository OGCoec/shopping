package com.example.ShoppingSystem.service.user.auth.passwordreset;

import com.example.ShoppingSystem.service.user.auth.passwordreset.mq.PasswordResetMailMessage;

public interface PasswordResetMailMessagePublisher {

    void publishResetCode(String email, String code, long expireMinutes);

    void publishResetLink(String email, String resetUrl, long expireMinutes);

    void publishResetCodeAndLink(String email,
                                 String code,
                                 String resetUrl,
                                 long codeExpireMinutes,
                                 long linkExpireMinutes);

    void publishRetry(PasswordResetMailMessage message, long delayMilli);

    void publishDeadLetter(PasswordResetMailMessage message);
}
