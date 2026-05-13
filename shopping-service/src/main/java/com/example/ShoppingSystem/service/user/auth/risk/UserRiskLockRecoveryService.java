package com.example.ShoppingSystem.service.user.auth.risk;

import java.time.Duration;

public interface UserRiskLockRecoveryService {

    int recoverStableUnlockedUsers(int lockCount, Duration stableDuration, int scoreBonus, int batchSize);

    int recoverStableUnlockedUsersByReason(String lockReason,
                                           String eventType,
                                           String eventReason,
                                           int lockCount,
                                           Duration stableDuration,
                                           int scoreBonus,
                                           int batchSize);
}
