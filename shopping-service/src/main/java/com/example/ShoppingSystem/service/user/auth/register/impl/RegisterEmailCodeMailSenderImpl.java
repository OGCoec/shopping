package com.example.ShoppingSystem.service.user.auth.register.impl;

import com.example.ShoppingSystem.service.user.auth.register.RegisterEmailCodeMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Synchronous mail sender used by the RabbitMQ consumer so send failures can be retried.
 */
@Service
public class RegisterEmailCodeMailSenderImpl implements RegisterEmailCodeMailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public RegisterEmailCodeMailSenderImpl(JavaMailSender mailSender,
                                           @Value("${spring.mail.username}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendRegisterEmailCode(String email, String code, long expireMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Registration Verification Code");
        message.setText(String.format(
                "Your registration verification code is %s. It expires in %d minutes.",
                code,
                expireMinutes
        ));
        mailSender.send(message);
    }
}
