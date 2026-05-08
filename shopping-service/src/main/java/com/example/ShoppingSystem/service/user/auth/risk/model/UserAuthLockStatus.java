package com.example.ShoppingSystem.service.user.auth.risk.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserAuthLockStatus {

    boolean blocked;
    boolean terminationRequired;
    Long retryAfterMs;
    String message;
    String status;
    String reason;

    public static UserAuthLockStatus allowed() {
        return UserAuthLockStatus.builder()
                .blocked(false)
                .terminationRequired(false)
                .build();
    }
}
