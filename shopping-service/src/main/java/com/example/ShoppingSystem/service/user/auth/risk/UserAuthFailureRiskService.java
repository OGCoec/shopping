package com.example.ShoppingSystem.service.user.auth.risk;

import com.example.ShoppingSystem.service.user.auth.risk.model.UserAuthFailureType;
import com.example.ShoppingSystem.service.user.auth.risk.model.UserAuthLockStatus;

public interface UserAuthFailureRiskService {

    UserAuthLockStatus checkLock(Long userId);

    UserAuthLockStatus checkAccountStatusAndLock(Long userId, String identityStatus);

    UserAuthLockStatus recordFailure(Long userId,
                                     UserAuthFailureType failureType,
                                     String ip,
                                     String deviceFingerprint);
}
