package com.example.ShoppingSystem.service.user.auth.risk.impl;

import com.example.ShoppingSystem.mapper.UserRiskProfileMapper;
import com.example.ShoppingSystem.service.user.auth.risk.UserRiskLockRecoveryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class UserRiskLockRecoveryServiceImpl implements UserRiskLockRecoveryService {

    private static final int MAX_BATCH_SIZE = 4000;

    private final UserRiskProfileMapper userRiskProfileMapper;

    public UserRiskLockRecoveryServiceImpl(UserRiskProfileMapper userRiskProfileMapper) {
        this.userRiskProfileMapper = userRiskProfileMapper;
    }

    @Override
    @Transactional
    public int recoverStableUnlockedUsers(int lockCount, Duration stableDuration, int scoreBonus, int batchSize) {
        if (lockCount <= 0 || stableDuration == null || stableDuration.isNegative() || stableDuration.isZero()) {
            return 0;
        }
        int safeLimit = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minus(stableDuration);
        int stableDays = Math.toIntExact(Math.max(1L, stableDuration.toDays()));
        return userRiskProfileMapper.recoverStableUnlockedUsers(
                lockCount,
                cutoff,
                Math.max(0, scoreBonus),
                stableDays,
                now,
                safeLimit
        );
    }

    @Override
    @Transactional
    public int recoverStableUnlockedUsersByReason(String lockReason,
                                                  String eventType,
                                                  String eventReason,
                                                  int lockCount,
                                                  Duration stableDuration,
                                                  int scoreBonus,
                                                  int batchSize) {
        if (lockCount <= 0 || stableDuration == null || stableDuration.isNegative() || stableDuration.isZero()
                || lockReason == null || lockReason.isBlank()
                || eventType == null || eventType.isBlank()
                || eventReason == null || eventReason.isBlank()) {
            return 0;
        }
        int safeLimit = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minus(stableDuration);
        int stableDays = Math.toIntExact(Math.max(1L, stableDuration.toDays()));
        return userRiskProfileMapper.recoverStableUnlockedUsersByReason(
                lockReason.trim(),
                eventType.trim(),
                eventReason.trim(),
                lockCount,
                cutoff,
                Math.max(0, scoreBonus),
                stableDays,
                now,
                safeLimit
        );
    }
}
