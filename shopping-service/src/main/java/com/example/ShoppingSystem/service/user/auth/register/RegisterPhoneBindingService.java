package com.example.ShoppingSystem.service.user.auth.register;

import com.example.ShoppingSystem.service.user.auth.register.model.RegisterPhoneBindingResult;

public interface RegisterPhoneBindingService {

    RegisterPhoneBindingResult sendPhoneBindCode(String flowId,
                                                 String preAuthToken,
                                                 String dialCode,
                                                 String phoneNumber,
                                                 String clientIp,
                                                 String riskLevel,
                                                 String deviceFingerprint,
                                                 String captchaUuid,
                                                 String captchaCode);

    RegisterPhoneBindingResult bindVerifiedPhone(String flowId,
                                                 String preAuthToken,
                                                 String dialCode,
                                                 String phoneNumber,
                                                 String smsCode);
}
