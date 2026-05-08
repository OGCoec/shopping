package com.example.ShoppingSystem.service.user.auth.phone.impl;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBindingAvailabilityService;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBoundCountingBloomService;
import org.springframework.stereotype.Service;

@Service
public class PhoneBindingAvailabilityServiceImpl implements PhoneBindingAvailabilityService {

    private final PhoneBoundCountingBloomService phoneBoundCountingBloomService;
    private final UserLoginIdentityMapper userLoginIdentityMapper;

    public PhoneBindingAvailabilityServiceImpl(PhoneBoundCountingBloomService phoneBoundCountingBloomService,
                                               UserLoginIdentityMapper userLoginIdentityMapper) {
        this.phoneBoundCountingBloomService = phoneBoundCountingBloomService;
        this.userLoginIdentityMapper = userLoginIdentityMapper;
    }

    @Override
    public PhoneBindingAvailability checkPhoneAvailable(String normalizedE164) {
        if (StrUtil.isBlank(normalizedE164)) {
            return blocked(ERROR_PHONE_ALREADY_BOUND, "This phone number is already in use.");
        }
        PhoneBoundCountingBloomService.PhoneBoundLookupResult lookupResult =
                phoneBoundCountingBloomService.lookupVerifiedPhone(normalizedE164);
        if (!lookupResult.available()) {
            return blocked(ERROR_PHONE_BOUND_BLOOM_UNAVAILABLE, "Phone existence filter is temporarily unavailable.");
        }
        if (!lookupResult.mightContain()) {
            return new PhoneBindingAvailability(true, null, null, "ok");
        }
        UserLoginIdentity existing = userLoginIdentityMapper.findByPhone(normalizedE164);
        if (existing != null) {
            return blocked(ERROR_PHONE_ALREADY_BOUND, "This phone number is already in use.");
        }
        return new PhoneBindingAvailability(true, null, null, "ok");
    }

    private PhoneBindingAvailability blocked(String errorCode, String message) {
        return new PhoneBindingAvailability(false, errorCode, errorCode, message);
    }
}
