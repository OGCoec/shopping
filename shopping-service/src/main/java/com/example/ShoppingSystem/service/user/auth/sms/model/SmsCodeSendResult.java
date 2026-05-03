package com.example.ShoppingSystem.service.user.auth.sms.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SmsCodeSendResult {

    boolean success;
    String message;
    String reasonCode;
    String normalizedE164;
}
