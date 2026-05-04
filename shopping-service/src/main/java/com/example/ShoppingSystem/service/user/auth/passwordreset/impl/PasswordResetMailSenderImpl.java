package com.example.ShoppingSystem.service.user.auth.passwordreset.impl;

import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetMailSenderImpl implements PasswordResetMailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public PasswordResetMailSenderImpl(JavaMailSender mailSender,
                                       @Value("${spring.mail.username}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendResetCode(String email, String code, long expireMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Password Reset Verification Code");
        message.setText(String.format(
                "Your password reset verification code is %s. It expires in %d minutes.",
                code,
                expireMinutes));
        mailSender.send(message);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendResetLink(String email, String resetUrl, long expireMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Password Reset Link");
        message.setText(String.format(
                "Use this link to reset your password: %s%n%nThis link expires in %d minutes.",
                resetUrl,
                expireMinutes));
        mailSender.send(message);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendResetCodeAndLink(String email,
                                     String code,
                                     String resetUrl,
                                     long codeExpireMinutes,
                                     long linkExpireMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Password Reset Verification");
        message.setText(String.format(
                "Your password reset verification code is %s. It expires in %d minutes.%n%n"
                        + "You can also use this link to reset your password: %s%n%n"
                        + "This link expires in %d minutes.",
                code,
                codeExpireMinutes,
                resetUrl,
                linkExpireMinutes));
        mailSender.send(message);
    }
}
