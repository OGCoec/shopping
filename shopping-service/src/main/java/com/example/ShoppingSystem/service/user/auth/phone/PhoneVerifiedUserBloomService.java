package com.example.ShoppingSystem.service.user.auth.phone;

public interface PhoneVerifiedUserBloomService {

    void rebuildFromDatabase();

    PhoneVerifiedUserLookupResult lookupPhoneVerifiedUser(Long userId);

    boolean mightContainPhoneVerifiedUser(Long userId);

    void addPhoneVerifiedUserAsync(Long userId);

    record PhoneVerifiedUserLookupResult(boolean available,
                                         boolean mightContain,
                                         String reasonCode) {
    }
}
