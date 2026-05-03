package com.example.ShoppingSystem.service.user.auth.sms;

import com.example.ShoppingSystem.service.user.auth.sms.model.SmsCodeSendResult;
import com.example.ShoppingSystem.service.user.auth.sms.model.SmsCodeVerifyResult;

public interface SmsCodeService {

    SmsCodeSendResult sendBindPhoneCode(String dialCode, String phoneNumber, String clientIp);

    SmsCodeVerifyResult verifyBindPhoneCode(String dialCode, String phoneNumber, String code);
}
