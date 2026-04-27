package com.example.ShoppingSystem.service.user.auth.register;

import com.example.ShoppingSystem.service.user.auth.register.model.RegisterCompletionResult;

/**
 * Completes register flow after email code verification.
 */
public interface RegisterCompletionService {

    RegisterCompletionResult verifyEmailCodeAndRegister(String email,
                                                        String emailCode,
                                                        String deviceFingerprint,
                                                        String requestIp);
}

