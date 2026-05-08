package com.example.ShoppingSystem.service.user.auth.phone;

public interface PhoneVerifiedUserLookupService {

    boolean isPhoneVerified(Long userId);

    boolean isPhoneVerified(Long userId, Boolean loadedPhoneVerified);

    void markPhoneVerified(Long userId);
}
