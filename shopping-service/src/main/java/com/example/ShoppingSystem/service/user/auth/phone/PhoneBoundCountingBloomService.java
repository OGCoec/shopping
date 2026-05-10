package com.example.ShoppingSystem.service.user.auth.phone;

import java.util.List;

public interface PhoneBoundCountingBloomService {

    void rebuildFromDatabase();

    PhoneBoundLookupResult lookupVerifiedPhone(String normalizedE164);

    boolean mightContainVerifiedPhone(String normalizedE164);

    void addVerifiedPhoneAsync(String normalizedE164);

    long removeVerifiedPhones(List<String> normalizedE164Phones);

    record PhoneBoundLookupResult(boolean available,
                                  boolean mightContain,
                                  String reasonCode) {
    }
}
