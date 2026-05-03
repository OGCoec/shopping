package com.example.ShoppingSystem.service.user.auth.phone;

public interface PhoneBoundCountingBloomService {

    void rebuildFromDatabase();

    PhoneBoundLookupResult lookupVerifiedPhone(String normalizedE164);

    boolean mightContainVerifiedPhone(String normalizedE164);

    void addVerifiedPhoneAsync(String normalizedE164);

    record PhoneBoundLookupResult(boolean available,
                                  boolean mightContain,
                                  String reasonCode) {
    }
}
