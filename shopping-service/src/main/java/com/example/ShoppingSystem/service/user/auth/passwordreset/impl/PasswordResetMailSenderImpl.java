package com.example.ShoppingSystem.service.user.auth.passwordreset.impl;

import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetMailMessagePublisher;
import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetMailSenderImpl implements PasswordResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailSenderImpl.class);

    private final PasswordResetMailMessagePublisher publisher;

    public PasswordResetMailSenderImpl(PasswordResetMailMessagePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void sendResetCode(String email, String code, long expireMinutes) {
        publishSafely("RESET_CODE", email, () -> publisher.publishResetCode(email, code, expireMinutes));
    }

    @Override
    public void sendResetLink(String email, String resetUrl, long expireMinutes) {
        publishSafely("RESET_LINK", email, () -> publisher.publishResetLink(email, resetUrl, expireMinutes));
    }

    @Override
    public void sendResetCodeAndLink(String email,
                                     String code,
                                     String resetUrl,
                                     long codeExpireMinutes,
                                     long linkExpireMinutes) {
        publishSafely("RESET_CODE_AND_LINK", email,
                () -> publisher.publishResetCodeAndLink(email, code, resetUrl, codeExpireMinutes, linkExpireMinutes));
    }

    private void publishSafely(String type, String email, Runnable publishAction) {
        try {
            publishAction.run();
        } catch (RuntimeException e) {
            log.error("Password reset mail message publish failed, type={}, email={}, error={}",
                    type, email, e.getMessage(), e);
        }
    }
}
