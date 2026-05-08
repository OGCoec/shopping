package com.example.ShoppingSystem.service.user.auth.phone;

public interface PhoneBindingAvailabilityService {

    String ERROR_PHONE_ALREADY_BOUND = "PHONE_ALREADY_BOUND";
    String ERROR_PHONE_BOUND_BLOOM_UNAVAILABLE = "PHONE_BOUND_BLOOM_UNAVAILABLE";

    PhoneBindingAvailability checkPhoneAvailable(String normalizedE164);

    record PhoneBindingAvailability(boolean allowed,
                                    String errorCode,
                                    String reasonCode,
                                    String message) {
    }
}
