package com.example.ShoppingSystem.service.user.auth.totp;

import com.example.ShoppingSystem.service.user.auth.totp.model.TotpSetupStartResult;
import com.example.ShoppingSystem.service.user.auth.totp.model.TotpVerificationResult;

public interface UserTotpService {

    TotpSetupStartResult startSetup(Long userId);

    TotpVerificationResult confirmSetup(Long userId, String code);

    TotpVerificationResult verify(Long userId, String code);

    boolean disable(Long userId);
}
