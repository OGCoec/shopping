package com.example.ShoppingSystem.service.user.auth.register.impl.RegisterWelcomeMailSender;

import com.example.ShoppingSystem.service.user.auth.register.RegisterWelcomeMailSender;
import com.example.ShoppingSystem.service.user.auth.register.WelcomeMailMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RegisterWelcomeMailSenderImpl implements RegisterWelcomeMailSender {

    private static final Logger log = LoggerFactory.getLogger(RegisterWelcomeMailSenderImpl.class);

    private final WelcomeMailMessagePublisher publisher;

    public RegisterWelcomeMailSenderImpl(WelcomeMailMessagePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void sendWelcomeMail(String email) {
        try {
            publisher.publishWelcomeMail(email);
        } catch (RuntimeException e) {
            log.error("Welcome mail message publish failed, email={}, error={}", email, e.getMessage(), e);
        }
    }
}
