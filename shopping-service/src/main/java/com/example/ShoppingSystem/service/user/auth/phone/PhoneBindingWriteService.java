package com.example.ShoppingSystem.service.user.auth.phone;

public interface PhoneBindingWriteService {

    String ERROR_PHONE_ALREADY_BOUND = "PHONE_ALREADY_BOUND";
    String ERROR_PHONE_BIND_BUSY = "PHONE_BIND_BUSY";
    String ERROR_PHONE_BIND_FAILED = "PHONE_BIND_FAILED";
    String ERROR_PHONE_BIND_IDENTITY_MISSING = "PHONE_BIND_IDENTITY_MISSING";
    String ERROR_PHONE_USER_ALREADY_VERIFIED = "PHONE_USER_ALREADY_VERIFIED";

    PhoneBindingResult bindVerifiedPhone(Long userId, String normalizedE164);

    record PhoneBindingResult(boolean success,
                              boolean alreadyVerified,
                              String errorCode,
                              String reasonCode,
                              String message,
                              String normalizedE164) {
    }
}
