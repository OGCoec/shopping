package com.example.ShoppingSystem.service.user.auth.risk;

import com.example.ShoppingSystem.service.user.auth.risk.model.UserAuthFailureType;
import com.example.ShoppingSystem.service.user.auth.risk.model.UserAuthLockStatus;

public interface UserAuthFailureRiskService {

    String REASON_ACCOUNT_SCORE_L6_BLOCKED = "ACCOUNT_SCORE_L6_BLOCKED";
    String MESSAGE_ACCOUNT_SCORE_L6_BLOCKED = "账号风险分过低，暂时无法使用。";

    UserAuthLockStatus checkLock(Long userId);

    UserAuthLockStatus checkAccountStatusAndLock(Long userId, String identityStatus);

    UserAuthLockStatus recordFailure(Long userId,
                                     UserAuthFailureType failureType,
                                     String ip,
                                     String deviceFingerprint);
}
