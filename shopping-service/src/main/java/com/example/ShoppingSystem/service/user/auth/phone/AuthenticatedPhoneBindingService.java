package com.example.ShoppingSystem.service.user.auth.phone;

import com.example.ShoppingSystem.service.user.auth.phone.model.AuthenticatedPhoneBindingResult;

public interface AuthenticatedPhoneBindingService {

    AuthenticatedPhoneBindingResult sendPhoneBindCode(Long userId,
                                                      String preAuthToken,
                                                      String dialCode,
                                                      String phoneNumber,
                                                      String clientIp,
                                                      String riskLevel,
                                                      String deviceFingerprint,
                                                      String captchaUuid,
                                                      String captchaCode);

    AuthenticatedPhoneBindingResult bindVerifiedPhone(Long userId,
                                                      String dialCode,
                                                      String phoneNumber,
                                                      String smsCode);
}
