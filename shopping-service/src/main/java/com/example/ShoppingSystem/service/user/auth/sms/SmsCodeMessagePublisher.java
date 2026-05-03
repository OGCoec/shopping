package com.example.ShoppingSystem.service.user.auth.sms;

import com.example.ShoppingSystem.service.user.auth.sms.mq.SmsCodeMessage;

public interface SmsCodeMessagePublisher {

    void publishSmsCode(SmsCodeMessage message);

    void publishRetry(SmsCodeMessage message, long delayMilli);

    void publishDeadLetter(SmsCodeMessage message);
}
