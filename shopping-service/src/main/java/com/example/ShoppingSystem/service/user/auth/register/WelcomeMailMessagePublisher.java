package com.example.ShoppingSystem.service.user.auth.register;

import com.example.ShoppingSystem.service.user.auth.register.mq.WelcomeMailMessage;

public interface WelcomeMailMessagePublisher {

    void publishWelcomeMail(String email);

    void publishRetry(WelcomeMailMessage message, long delayMilli);

    void publishDeadLetter(WelcomeMailMessage message);
}
