package com.example.ShoppingSystem.service.user.auth.sms;

import com.example.ShoppingSystem.service.user.auth.sms.model.PhoneSmsRiskGateResult;

public interface PhoneSmsRiskGateService {

    String SCENE_BIND_PHONE_SMS = "BIND_PHONE_SMS";
    String SCENE_PHONE_LOGIN_SMS = "PHONE_LOGIN_SMS";

    PhoneSmsRiskGateResult checkOrVerify(String scene,
                                         String normalizedPhone,
                                         String preAuthToken,
                                         String deviceFingerprint,
                                         String riskLevel,
                                         String remoteIp,
                                         String captchaUuid,
                                         String captchaCode);
}
